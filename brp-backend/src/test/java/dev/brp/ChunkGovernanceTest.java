package dev.brp;

import dev.brp.common.BadRequestException;
import dev.brp.dto.ChunkDtos.ChunkSlotResponse;
import dev.brp.dto.ChunkDtos.GovernorStatusResponse;
import dev.brp.dto.ChunkDtos.GrantMinutesRequest;
import dev.brp.dto.ChunkDtos.PowerRequest;
import dev.brp.dto.ChunkDtos.RegisterChunkRequest;
import dev.brp.service.ChunkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The governor's decision table, end to end (docs/09 section 4): register
 * idempotently, feed the meter, flip the remote switch, and never let a
 * verdict touch a player's legs.
 */
@SpringBootTest
class ChunkGovernanceTest {

    @Autowired ChunkService chunks;

    private String tenant() {
        return "g-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void registrationIsIdempotentPerSlotKey() {
        String t = tenant();
        var req = new RegisterChunkRequest("minecraft:overworld", "10,-3", "uuid-1");
        ChunkSlotResponse first = chunks.register(t, req);
        ChunkSlotResponse again = chunks.register(t, req);
        assertThat(again.chunkKey()).isEqualTo(first.chunkKey());
        assertThat(chunks.slots(t)).hasSize(1);
    }

    @Test
    void freshSlotHaltsWithNoCredit() {
        String t = tenant();
        chunks.register(t, new RegisterChunkRequest("minecraft:overworld", "0,0", "u"));
        List<GovernorStatusResponse> v = chunks.governor(t);
        assertThat(v).hasSize(1);
        assertThat(v.get(0).verdict()).isEqualTo(ChunkService.VERDICT_HALT);
        assertThat(v.get(0).reason()).isEqualTo("NO_CREDIT");
    }

    @Test
    void lowCreditThrottlesAndHealthyCreditRuns() {
        String t = tenant();
        chunks.register(t, new RegisterChunkRequest("minecraft:the_nether", "5,5", "u"));
        chunks.grantMinutes(t, "minecraft:the_nether", "5,5", new GrantMinutesRequest(30));
        assertThat(chunks.governor(t).get(0).verdict()).isEqualTo(ChunkService.VERDICT_THROTTLE);

        chunks.grantMinutes(t, "minecraft:the_nether", "5,5", new GrantMinutesRequest(500));
        assertThat(chunks.governor(t).get(0).verdict()).isEqualTo(ChunkService.VERDICT_RUN);
        assertThat(chunks.governor(t).get(0).reason()).isEqualTo("OK");
    }

    @Test
    void adminRemoteSwitchHaltsEvenWithFullCredit() {
        String t = tenant();
        chunks.register(t, new RegisterChunkRequest("minecraft:overworld", "1,1", "u"));
        chunks.grantMinutes(t, "minecraft:overworld", "1,1", new GrantMinutesRequest(1000));
        chunks.setPower(t, "minecraft:overworld", "1,1", new PowerRequest(false));

        var verdict = chunks.governor(t).get(0);
        assertThat(verdict.verdict()).isEqualTo(ChunkService.VERDICT_HALT);
        assertThat(verdict.reason()).isEqualTo("DISABLED_BY_ADMIN");

        chunks.setPower(t, "minecraft:overworld", "1,1", new PowerRequest(true));
        assertThat(chunks.governor(t).get(0).verdict()).isEqualTo(ChunkService.VERDICT_RUN);
    }

    @Test
    void verdictsStayInsideTheirTenant() {
        String a = tenant();
        String b = tenant();
        chunks.register(a, new RegisterChunkRequest("minecraft:overworld", "2,2", "u"));
        chunks.grantMinutes(a, "minecraft:overworld", "2,2", new GrantMinutesRequest(1000));
        assertThat(chunks.governor(b)).isEmpty();
        assertThat(chunks.governor(a)).hasSize(1);
    }

    @Test
    void grantsRefuseNegativeTimeAndUnknownSlotsStayUnknown() {
        String t = tenant();
        chunks.register(t, new RegisterChunkRequest("minecraft:overworld", "3,3", "u"));
        assertThatThrownBy(() -> chunks.grantMinutes(t, "minecraft:overworld", "3,3",
                new GrantMinutesRequest(-5)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> chunks.setPower(t, "minecraft:overworld", "404,404",
                new PowerRequest(true)))
                .isInstanceOf(BadRequestException.class);
    }
}
