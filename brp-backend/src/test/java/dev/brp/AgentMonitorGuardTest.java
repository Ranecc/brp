package dev.brp;

import dev.brp.common.BadRequestException;
import dev.brp.entity.AgentStateEntity;
import dev.brp.repository.AgentStateRepository;
import dev.brp.service.impl.AgentMonitorServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Audit #27: the fleet average used to count corpses (an agent silent for a
 * month still moved the mean) and accepted any double the wire produced.
 * Now: five minutes of silence retires an agent from the average, and
 * out-of-band heartbeats are refused at the door (red line 2 — measured,
 * never invented).
 */
@SpringBootTest
class AgentMonitorGuardTest {

    @Autowired AgentMonitorServiceImpl monitor;
    @Autowired AgentStateRepository agents;
    @Autowired TransactionTemplate tx;

    private void backdate(String agentId, Duration ago) {
        tx.executeWithoutResult(status -> {
            AgentStateEntity agent = agents.findById(agentId).orElseThrow();
            agent.setReportedAt(Instant.now().minus(ago));
            agents.save(agent);
        });
    }

    @Test
    void corpsesLeaveTheAverage() {
        String fresh = "fresh-" + uniqueId();
        String corpse = "corpse-" + uniqueId();
        monitor.recordHeartbeat(fresh, 40.0, 20.0, 0);
        // other suites share this database and own fresh heartbeats too, so
        // the absolute mean is nobody's business — the corpse must simply
        // change nothing: same fleet, same average, exactly
        double withoutCorpse = monitor.averageMspt();
        monitor.recordHeartbeat(corpse, 900.0, 20.0, 0);
        backdate(corpse, Duration.ofMinutes(30));
        // a corpse with 900 mspt lands in the table and the mean does not move
        assertThat(monitor.averageMspt()).isEqualTo(withoutCorpse);
    }

    @Test
    void outOfRangeHeartbeatsAreRefused() {
        String agent = "range-" + uniqueId();
        assertThatThrownBy(() -> monitor.recordHeartbeat(agent, -1.0, 20.0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> monitor.recordHeartbeat(agent, Double.NaN, 20.0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> monitor.recordHeartbeat(agent, 99_999.0, 20.0, 0))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> monitor.recordHeartbeat(agent, 40.0, -5.0, 0))
                .isInstanceOf(BadRequestException.class);
        // the sane one lands
        monitor.recordHeartbeat(agent, 40.0, 20.0, 3);
        assertThat(agents.findById(agent)).isPresent();
    }

    private static String uniqueId() {
        return java.util.UUID.randomUUID().toString();
    }
}
