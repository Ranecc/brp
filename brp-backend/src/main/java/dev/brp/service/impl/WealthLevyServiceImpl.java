package dev.brp.service.impl;

import dev.brp.common.ConflictException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.MonetaryDtos.WealthAssessmentRow;
import dev.brp.entity.WealthAssessmentEntity;
import dev.brp.repository.SnapshotTenantRepository;
import dev.brp.repository.WealthAssessmentRepository;
import dev.brp.service.LedgerService;
import dev.brp.service.NotificationService;
import dev.brp.service.RegimeService;
import dev.brp.service.WealthLevyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * The wealth levy (docs/12 §6): step brackets on treasury wealth, hoarding
 * surcharge when velocity < 0.5, regime-gated, one journal per (week,
 * tenant) with idem wealth:{tenant}:{week}. INACTIVE weeks still record —
 * clemency is also a record.
 */
@Service
public class WealthLevyServiceImpl implements WealthLevyService {

    private final WealthAssessmentRepository assessments;
    private final SnapshotTenantRepository tenants;
    private final RegimeService regime;
    private final LedgerService ledger;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final double hoardingSurcharge;

    public WealthLevyServiceImpl(WealthAssessmentRepository assessments,
                                 SnapshotTenantRepository tenants,
                                 RegimeService regime,
                                 LedgerService ledger,
                                 NotificationService notifications,
                                 TransactionTemplate tx,
                                 @Value("${brp.levy.wealth.hoarding-surcharge:0.5}") double hoardingSurcharge) {
        this.assessments = assessments;
        this.tenants = tenants;
        this.regime = regime;
        this.ledger = ledger;
        this.notifications = notifications;
        this.tx = tx;
        this.hoardingSurcharge = hoardingSurcharge;
    }

    @Override
    public List<WealthAssessmentRow> assess(String weekOf) {
        boolean armed = regime.active();
        return tenants.findByWeekOf(weekOf).stream().map(t -> {
            long amount = armed ? bracketTax(t.getWealthCents(), t.getVelocity()) : 0;
            boolean surcharge = armed && t.getVelocity() < 0.5 && amount > 0;
            try {
                return tx.execute(status -> {
                    assessments.saveAndFlush(new WealthAssessmentEntity(
                            weekOf, t.getTenantId(), t.getWealthCents(), surcharge, amount, false));
                    if (amount > 0) {
                        ensureSink(t.getTenantId());
                        ledger.post(t.getTenantId(), new PostJournalRequest(
                                "wealth:" + t.getTenantId() + ":" + weekOf, "BRC", List.of(
                                        new JournalLine("expense-sink", "DEBIT", amount, null),
                                        new JournalLine("treasury", "CREDIT", amount, null))));
                        notifications.enqueue("monetary.wealth.levied", "monetary.wealth.levied",
                                t.getTenantId(), amount);
                    }
                    assessments.save(new WealthAssessmentEntity(
                            weekOf, t.getTenantId(), t.getWealthCents(), surcharge, amount, true));
                    return row(weekOf, t.getTenantId(), t.getWealthCents(), surcharge, amount);
                });
            } catch (DataIntegrityViolationException already) {
                return row(weekOf, t.getTenantId(), t.getWealthCents(), surcharge, amount);
            }
        }).toList();
    }

    /** Step brackets (docs/03 §9): <=1M 0% | <=10M 0.5% | >10M 1.5%, +surcharge. */
    long bracketTax(long wealthCents, double velocity) {
        long tax = 0;
        if (wealthCents > 1_000_000) {
            tax += (Math.min(wealthCents, 10_000_000L) - 1_000_000L) * 5 / 1000;
        }
        if (wealthCents > 10_000_000) {
            tax += (wealthCents - 10_000_000L) * 15 / 1000;
        }
        if (velocity < 0.5 && tax > 0) {
            tax += tax * (long) Math.round(hoardingSurcharge * 100) / 100;
        }
        return tax;
    }

    @Override
    public List<WealthAssessmentRow> assessments(String weekOf) {
        return assessments.findByWeekOf(weekOf).stream().map(a ->
                new WealthAssessmentRow(a.getWeekOf(), a.getTenantId(), a.getBracketCents(),
                        a.isSurcharge(), a.getAmountCents(), a.isPosted())).toList();
    }

    private WealthAssessmentRow row(String weekOf, String tenantId, long bracket,
                                    boolean surcharge, long amount) {
        return new WealthAssessmentRow(weekOf, tenantId, bracket, surcharge, amount, true);
    }

    private void ensureSink(String tenantId) {
        try {
            ledger.createAccount(tenantId, new CreateAccountRequest("expense-sink", "SYSTEM_SINK", "BRC"));
        } catch (ConflictException alreadyThere) {
            // sinks are patient
        }
    }
}
