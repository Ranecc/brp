package dev.brp.service.impl;

import dev.brp.dto.ReconciliationDtos.FindingResponse;
import dev.brp.dto.ReconciliationDtos.RunReport;
import dev.brp.entity.AccountEntity;
import dev.brp.entity.ReconciliationFindingEntity;
import dev.brp.repository.AccountRepository;
import dev.brp.repository.ReconciliationFindingRepository;
import dev.brp.service.NotificationService;
import dev.brp.service.ReconciliationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The reconciliation desk, v1 (docs/10 R-1): double entry says Σ balances
 * must be zero per currency. When it is not, the desk files the finding and
 * pages management — the books are wrong and everyone should know.
 */
@Service
public class ReconciliationServiceImpl implements ReconciliationService {

    private final AccountRepository accounts;
    private final ReconciliationFindingRepository findings;
    private final NotificationService notifications;

    public ReconciliationServiceImpl(AccountRepository accounts,
                                     ReconciliationFindingRepository findings,
                                     NotificationService notifications) {
        this.accounts = accounts;
        this.findings = findings;
        this.notifications = notifications;
    }

    @Override
    @dev.brp.common.annotation.Atomic
    public RunReport reconcile() {
        Map<String, Long> sums = accounts.findAll().stream()
                .collect(Collectors.groupingBy(AccountEntity::getCurrency,
                        Collectors.summingLong(AccountEntity::getBalanceCents)));
        int filed = 0;
        for (var entry : sums.entrySet()) {
            long imbalance = entry.getValue();
            if (imbalance != 0) {
                findings.save(new ReconciliationFindingEntity(entry.getKey(), imbalance));
                notifications.enqueue("reconciliation.imbalance", "reconciliation.imbalance",
                        entry.getKey(), imbalance);
                filed++;
            }
        }
        return new RunReport(sums.size(), filed);
    }

    @Override
    public List<FindingResponse> findings() {
        return findings.findTop50ByOrderByIdDesc().stream()
                .map(f -> new FindingResponse(f.getId(), f.getCurrency(),
                        f.getImbalanceCents(), f.getStatus(), String.valueOf(f.getCreatedAt())))
                .toList();
    }

    /** The desk opens hourly, like all good back offices. */
    @Scheduled(fixedDelayString = "${brp.reconciliation.interval-ms:3600000}")
    public void scheduled() {
        reconcile();
    }
}
