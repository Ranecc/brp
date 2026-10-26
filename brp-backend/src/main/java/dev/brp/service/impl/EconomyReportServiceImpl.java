package dev.brp.service.impl;

import dev.brp.common.annotation.Atomic;
import dev.brp.dto.EconomyDtos.EconomyDailyResponse;
import dev.brp.entity.AccountEntity;
import dev.brp.entity.EconomyDailyEntity;
import dev.brp.entity.JournalEntryEntity;
import dev.brp.repository.AccountRepository;
import dev.brp.repository.EconomyDailyRepository;
import dev.brp.repository.JournalEntryRepository;
import dev.brp.service.EconomyReportService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Daily economy materialization (docs/03 sections 2-3), from the ledger only —
 * if it didn't journal, it didn't happen:
 * - M (money mass)  = Σ balances of held accounts (PLAYER/TEAM/ESCROW);
 *   double-entry keeps the faucet+sink mirror at exactly −M
 * - net emission    = Δ held balances today (what the printer printed minus burned)
 * - traded volume   = Σ DEBIT amounts on held accounts today
 * - velocity V      = traded volume / M (Fisher's MV=PQ, the paranoia channel)
 */
@Service
public class EconomyReportServiceImpl implements EconomyReportService {

    private static final Set<String> HELD = Set.of("PLAYER", "TEAM", "ESCROW");

    private final AccountRepository accounts;
    private final JournalEntryRepository journal;
    private final EconomyDailyRepository daily;

    public EconomyReportServiceImpl(AccountRepository accounts,
                                    JournalEntryRepository journal,
                                    EconomyDailyRepository daily) {
        this.accounts = accounts;
        this.journal = journal;
        this.daily = daily;
    }

    @Override
    @Atomic
    public EconomyDailyResponse materializeToday(String currency) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<AccountEntity> books = accounts.findAll().stream()
                .filter(a -> currency.equals(a.getCurrency())).toList();
        long moneyMass = books.stream()
                .filter(a -> HELD.contains(a.getAccountType()))
                .mapToLong(AccountEntity::getBalanceCents).sum();

        Map<Long, AccountEntity> byId = books.stream()
                .collect(Collectors.toMap(AccountEntity::getId, Function.identity()));
        long netEmission = 0;
        long tradedVolume = 0;
        for (JournalEntryEntity e : journal.findByCreatedAtGreaterThanEqual(
                today.atStartOfDay().toInstant(ZoneOffset.UTC))) {
            AccountEntity acc = byId.get(e.getAccountId());
            if (acc == null || !HELD.contains(acc.getAccountType())) {
                continue;
            }
            long signed = "DEBIT".equals(e.getDirection()) ? e.getAmountCents() : -e.getAmountCents();
            netEmission += signed;
            if ("DEBIT".equals(e.getDirection())) {
                tradedVolume += e.getAmountCents();
            }
        }
        double velocity = moneyMass > 0 ? (double) tradedVolume / moneyMass : 0.0;

        EconomyDailyEntity row = daily.findByReportDateAndCurrency(today, currency).orElse(null);
        if (row == null) {
            row = new EconomyDailyEntity(today, currency, 0, 0, 0, 0.0);
        }
        row.refresh(moneyMass, netEmission, tradedVolume, velocity);
        daily.save(row);
        return toDto(row);
    }

    @Override
    public List<EconomyDailyResponse> history(String currency) {
        return daily.findAll().stream()
                .filter(r -> r.getCurrency().equals(currency))
                .map(EconomyReportServiceImpl::toDto)
                .toList();
    }

    private static EconomyDailyResponse toDto(EconomyDailyEntity r) {
        return new EconomyDailyResponse(r.getReportDate(), r.getCurrency(),
                r.getMoneyMassCents(), r.getNetEmissionCents(),
                r.getTradedVolumeCents(), r.getVelocity());
    }
}
