package dev.brp;

import dev.brp.common.BadRequestException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.dto.TeamDtos.CreateTeamRequest;
import dev.brp.entity.LevyMinuteEntity;
import dev.brp.entity.TeamMemberRow;
import dev.brp.repository.LevyMinuteRepository;
import dev.brp.repository.TeamMemberRepository;
import dev.brp.service.DemandResponseService;
import dev.brp.service.LedgerService;
import dev.brp.service.ShopFloorService;
import dev.brp.service.TeamService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The shop floor desk (docs/13 §9): the downtime ledger feeds OEE (A
 * measured, P against the trailing baseline, Q disclosed as 1.0); assets
 * register with cost basis; work-order stamps price machine time off the
 * levy minutes already in the books.
 */
@SpringBootTest
class ShopFloorTest {

    @Autowired ShopFloorService shopFloor;
    @Autowired LevyMinuteRepository levyMinutes;
    @Autowired LedgerService ledger;
    @Autowired TeamService teams;
    @Autowired TeamMemberRepository memberRepo;
    @Autowired Clock clock;
    @Autowired dev.brp.repository.DowntimeEventRepository downtimeRepo;

    private String fundedTenant() {
        String tenant = "sf-" + UUID.randomUUID().toString().substring(0, 8);
        String member = UUID.randomUUID().toString();
        teams.create(new CreateTeamRequest(UUID.randomUUID().toString(), tenant,
                "Floor Co", "FLR", UUID.randomUUID().toString()));
        memberRepo.save(new TeamMemberRow(tenant, member, "MEMBER"));
        ledger.createAccount(tenant, new CreateAccountRequest(member, "PLAYER", "BRC"));
        ledger.post(tenant, new PostJournalRequest(UUID.randomUUID().toString(), "BRC", List.of(
                new JournalLine("treasury", "DEBIT", 1_000_000L, "seed"),
                new JournalLine(member, "CREDIT", 1_000_000L, "seed"))));
        return tenant;
    }

    private LocalDateTime monday() {
        return LocalDate.now(clock).with(java.time.DayOfWeek.MONDAY).atStartOfDay();
    }

    @Test
    void downtimeReplaysAreIdempotentByKey() {
        String tenant = fundedTenant();
        LocalDateTime now = LocalDateTime.now(clock);
        shopFloor.recordDowntime(tenant, "farm_north", "DR_SHED", "dt-1", now, now.plusHours(1));
        shopFloor.recordDowntime(tenant, "farm_north", "DR_SHED", "dt-1", now, now.plusHours(1));
        assertThat(shopFloor.downtime(tenant)).hasSize(1);
    }

    @Test
    void oeeCombinesDowntimeWithBaselineAndDisclosesQuality() {
        String tenant = fundedTenant();
        LocalDateTime monday = monday();
        // this week: 1 day down (1440 min), 600 units produced
        shopFloor.recordDowntime(tenant, "*", "DR_SHED", "oee-1", monday, monday.plusDays(1));
        levyMinutes.save(new LevyMinuteEntity(monday.plusHours(2), tenant, 600.0));

        // four baseline weeks of 1_000 units each
        for (int i = 1; i <= 4; i++) {
            levyMinutes.save(new LevyMinuteEntity(monday.minusWeeks(i).plusHours(2), tenant, 1_000.0));
        }

        var report = shopFloor.oee(tenant, monday.toLocalDate());
        assertThat(report.downtimeMinutes()).isEqualTo(1_440);
        assertThat(report.availability()).isCloseTo(1.0 - 1_440.0 / 10_080,
                org.assertj.core.data.Offset.offset(0.0001));
        assertThat(report.performance()).isCloseTo(0.6, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(report.quality()).isEqualTo(1.0);   // disclosed, not measured
        assertThat(report.performanceColdStart()).isFalse();
        assertThat(report.oee()).isCloseTo(report.availability() * 0.6,
                org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void oeeColdStartDisclosesPerformanceAsOne() {
        String tenant = fundedTenant();
        var report = shopFloor.oee(tenant, monday().toLocalDate());
        assertThat(report.performanceColdStart()).isTrue();
        assertThat(report.performance()).isEqualTo(1.0);
        assertThat(report.oee()).isEqualTo(report.availability());
    }

    @Test
    void assetsRegisterAndRetire() {
        String tenant = fundedTenant();
        var asset = shopFloor.registerAsset(tenant, "hall", "The Great Hall", "minecraft:overworld|10,10", 500_000L);
        assertThat(asset.getStatus()).isEqualTo("IN_SERVICE");
        assertThat(shopFloor.assets(tenant)).hasSize(1);

        var retired = shopFloor.retire(tenant, asset.getId());
        assertThat(retired.getStatus()).isEqualTo("RETIRED");

        String other = fundedTenant();
        assertThatThrownBy(() -> shopFloor.retire(other, asset.getId()))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("asset.unknown");
    }

    @Test
    void workorderStampsPriceMachineTimeFromTheLevy() {
        String tenant = fundedTenant();
        LocalDateTime monday = monday();
        levyMinutes.save(new LevyMinuteEntity(monday.plusHours(1), tenant, 100.0));  // 100 min
        // the levy priced those minutes: 100 units at the levy's bookkeeping —
        // use the amount the levy itself wrote; seed one with a known stamp
        var minute = levyMinutes.findByMinuteBucketBetween(monday, monday.plusWeeks(1)).get(0);
        minute.assess(0.0, 5_000L);
        levyMinutes.save(minute);

        var stamp = shopFloor.stampCompletionCost(tenant, "wo-42", monday,
                monday.plusWeeks(1), 12_345L);

        assertThat(stamp.getMachineCostCents()).isEqualTo(5_000L);
        assertThat(stamp.getMaterialCostCents()).isEqualTo(12_345L);
        // write-once: a restamp is revisionism
        assertThatThrownBy(() -> shopFloor.stampCompletionCost(tenant, "wo-42",
                monday, monday.plusWeeks(1), 1L))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("already.stamped");
    }

    @Test
    void downtimeWindowValidationRefusesZeroDuration() {
        String tenant = fundedTenant();
        LocalDateTime now = LocalDateTime.now(clock);
        assertThatThrownBy(() -> shopFloor.recordDowntime(tenant, "*", "MANUAL", "dt-zero", now, now))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("downtime.window.invalid");
    }
}
