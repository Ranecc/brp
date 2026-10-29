package dev.brp;

import dev.brp.common.BadRequestException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.entity.BondSeriesEntity;
import dev.brp.entity.BondSubscriptionEntity;
import dev.brp.entity.TeamMemberRow;
import dev.brp.repository.BondAccrualRepository;
import dev.brp.repository.BondSeriesRepository;
import dev.brp.repository.BondSubscriptionRepository;
import dev.brp.repository.CreditAccrualRunRepository;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.service.BondService;
import dev.brp.service.CreditService;
import dev.brp.service.LedgerService;
import dev.brp.service.LoadSampleService;
import dev.brp.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The bond desk end to end (docs/13 §7): escrow -> award -> daily accrual
 * (idempotent per day) -> settlement against MEASURED mspt, principal always
 * whole. The exact performance-factor math lives in
 * {@link dev.brp.service.impl.BondMathTest}; here the settlement window's
 * average includes every other test's heartbeats, so the coupon is asserted
 * within its factor band, to no fictional precision.
 */
@SpringBootTest
class BondLifecycleTest {

    @Autowired BondService bonds;
    @Autowired BondSeriesRepository seriesRepo;
    @Autowired BondSubscriptionRepository subs;
    @Autowired BondAccrualRepository accruals;
    @Autowired LedgerService ledger;
    @Autowired LoadSampleService loadSamples;
    @Autowired CreditService credit;
    @Autowired CreditAccrualRunRepository creditRuns;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;
    @Autowired Clock clock;

    private String fundedTenant() {
        String tenant = "bd-" + UUID.randomUUID().toString().substring(0, 8);
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Bond Co", "BND", UUID.randomUUID().toString()));
        memberRepo.save(new TeamMemberRow(tenant, member, "MEMBER"));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 1_000_000L, "seed"),
                new JournalLine(member, "CREDIT", 1_000_000L, "seed"))));
        return tenant;
    }

    private long treasury(String tenant) {
        return ledger.listAccounts(tenant).stream()
                .filter(a -> a.accountNo().equals("treasury"))
                .findFirst().orElseThrow().balanceCents();
    }

    @Test
    void escrowAwardAccrueSettle() {
        String tenant = fundedTenant();
        loadSamples.record(20.0, 1, 1, 1, 1);   // a cool server: factor will cap at 1.0

        BondSeriesEntity series = bonds.issue("series-" + UUID.randomUUID().toString().substring(0, 6),
                10_000L, 12, 1, 1L, 45.0);
        long before = treasury(tenant);

        BondSubscriptionEntity sub = bonds.subscribe(series.getId(), tenant, 1L);
        assertThat(sub.getFilledCents()).isEqualTo(10_000L);
        assertThat(treasury(tenant)).isEqualTo(before - 10_000L);   // escrowed

        bonds.close(series.getId());
        assertThat(bonds.get(series.getId()).getStatus()).isEqualTo("AWARDED");

        assertThat(bonds.accrueDaily()).isTrue();
        bonds.accrueDaily();   // same day again: the PK is the idem, no double coupon
        long accrued = accruals.findBySeriesIdAndTenantId(series.getId(), tenant).stream()
                .mapToLong(a -> a.getAmountCents()).sum();
        assertThat(accrued).isEqualTo(3L);   // 10_000 * 12% / 365 = 3.29, floored

        // age the series past maturity (the API dates it forward; tests re-date)
        var managed = seriesRepo.findById(series.getId()).orElseThrow();
        managed.setMaturesAt(java.time.OffsetDateTime.now(clock).minusMinutes(1));
        seriesRepo.save(managed);
        List<Long> settled = bonds.settleMatured();
        assertThat(settled).contains(series.getId());

        BondSubscriptionEntity after = subs.findBySeriesIdAndTenantId(series.getId(), tenant).orElseThrow();
        assertThat(after.getPrincipalPaidCents()).isEqualTo(10_000L);
        assertThat(after.getCouponPaidCents()).isBetween(accrued / 2, accrued);   // factor band
        assertThat(bonds.get(series.getId()).getStatus()).isEqualTo("REDEEMED");
        long delta = treasury(tenant) - (before - 10_000L);
        assertThat(delta).isEqualTo(after.getPrincipalPaidCents() + after.getCouponPaidCents());
    }

    @Test
    void starvingBooksFailAndRefund() {
        String tenant = fundedTenant();
        BondSeriesEntity series = bonds.issue("tiny-" + UUID.randomUUID().toString().substring(0, 6),
                10_000L, 12, 7, 100L, 45.0);
        long before = treasury(tenant);
        bonds.subscribe(series.getId(), tenant, 1L);

        bonds.close(series.getId());

        assertThat(bonds.get(series.getId()).getStatus()).isEqualTo("FAILED");
        assertThat(treasury(tenant)).isEqualTo(before);   // every cent refunded
    }

    @Test
    void oversubscriptionAndDoubleSubscriptionAreRefused() {
        String tenant = fundedTenant();
        BondSeriesEntity series = bonds.issue("one-" + UUID.randomUUID().toString().substring(0, 6),
                10_000L, 12, 7, 1L, 45.0);
        bonds.subscribe(series.getId(), tenant, 1L);
        assertThatThrownBy(() -> bonds.subscribe(series.getId(), tenant, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bond");
        String other = fundedTenant();
        assertThatThrownBy(() -> bonds.subscribe(series.getId(), other, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("bond.oversubscribed");
    }

    @Test
    void restrictedCreditBlocksNewSubscriptions() {
        String tenant = fundedTenant();
        String member = memberRepo.findAll().stream()
                .filter(m -> m.getTeamId().equals(tenant) && "MEMBER".equals(m.getRole()))
                .findFirst().orElseThrow().getPlayerUuid();
        credit.setLimit(tenant, 1_000_000L);
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine(member, "DEBIT", 2_000_000L, "spend"),
                new JournalLine("treasury", "CREDIT", 2_000_000L, "spend"))));
        creditRuns.deleteGuard(java.time.LocalDate.now(clock));   // own the day's guard
        credit.runDailyDesk();
        assertThat(credit.blocked(tenant)).isTrue();

        BondSeriesEntity series = bonds.issue("locked-" + UUID.randomUUID().toString().substring(0, 6),
                10_000L, 12, 7, 10L, 45.0);
        assertThatThrownBy(() -> bonds.subscribe(series.getId(), tenant, 1L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("credit.restricted");
    }
}
