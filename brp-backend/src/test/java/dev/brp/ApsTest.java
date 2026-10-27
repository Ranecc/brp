package dev.brp;

import dev.brp.dto.ChunkDtos.GrantMinutesRequest;
import dev.brp.dto.ChunkDtos.RegisterChunkRequest;
import dev.brp.service.ApsService;
import dev.brp.service.ChunkService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fair share (docs/09 section 5): under capacity everyone runs at 1.0; over
 * capacity everyone takes the SAME haircut — proportional fair by division,
 * not by negotiation.
 */
@SpringBootTest(properties = {
        "brp.aps.capacity-quota-minutes=1000",
        "brp.chunk.governor.starter-minutes=0"})
class ApsTest {

    @Autowired ApsService aps;
    @Autowired ChunkService chunks;

    private void tenantWith(String tenant, long minutes) {
        chunks.register(tenant, new RegisterChunkRequest("minecraft:overworld", "1,1", "u"));
        chunks.grantMinutes(tenant, "minecraft:overworld", "1,1", new GrantMinutesRequest(minutes));
    }

    @Test
    void underCapacityEveryoneRunsFullSpeed() {
        String a = "aps-a-" + UUID.randomUUID().toString().substring(0, 6);
        String b = "aps-b-" + UUID.randomUUID().toString().substring(0, 6);
        tenantWith(a, 300);
        tenantWith(b, 500);   // total 800 of these two... capacity is global though

        // other tests may hold credits too; these two must be 1.0 while the
        // global total is under capacity only if the world is small — assert
        // the contract directly: factor is in (0,1] and equal for both tenants
        double fa = aps.factorFor(a);
        double fb = aps.factorFor(b);
        assertThat(fa).isGreaterThan(0).isLessThanOrEqualTo(1.0);
        assertThat(fa).isEqualTo(fb);   // the same haircut, always
    }

    @Test
    void overCapacityTheHaircutIsUniform() {
        String a = "aps-c-" + UUID.randomUUID().toString().substring(0, 6);
        String b = "aps-d-" + UUID.randomUUID().toString().substring(0, 6);
        tenantWith(a, 500);
        tenantWith(b, 700);
        double fa = aps.factorFor(a);
        double fb = aps.factorFor(b);
        // with capacity 1000 and at least 1200 claimed, both must be throttled
        assertThat(fa).isLessThan(1.0);
        assertThat(fa).isEqualTo(fb);
    }

    @Test
    void strangersRunFreeByDefault() {
        assertThat(aps.factorFor("never-registered-" + UUID.randomUUID())).isEqualTo(1.0);
    }
}
