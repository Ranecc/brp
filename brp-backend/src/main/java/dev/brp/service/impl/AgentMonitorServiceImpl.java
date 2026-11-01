package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.common.annotation.Atomic;
import dev.brp.entity.AgentStateEntity;
import dev.brp.repository.AgentStateRepository;
import dev.brp.service.AgentMonitorService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Latest-known-state per agent. Since audit #27 the average only counts
 * agents heard from inside the staleness window (a corpse from a month ago
 * is not a fleet), and heartbeats outside the sane band are refused at the
 * door — the load plane reports measured numbers, never invented ones
 * (docs/01 red line 2).
 */
@Service
public class AgentMonitorServiceImpl implements AgentMonitorService {

    /** Heartbeats land every ~30s; five minutes of silence means the corpse rule. */
    public static final Duration STALE_AFTER = Duration.ofMinutes(5);
    /** mspt ceiling: a healthy box idles near 0-50; 10k is already a meltdown. */
    static final double MSPT_CEILING = 10_000.0;
    static final double TPS_CEILING = 1_000.0;
    static final int BACKLOG_CEILING = 100_000_000;

    private final AgentStateRepository agents;
    private final Clock clock;

    public AgentMonitorServiceImpl(AgentStateRepository agents, Clock clock) {
        this.agents = agents;
        this.clock = clock;
    }

    @Override
    @Atomic
    public void recordHeartbeat(String agentId, double mspt, double tps, int backlog) {
        if (!Double.isFinite(mspt) || mspt < 0 || mspt > MSPT_CEILING
                || !Double.isFinite(tps) || tps < 0 || tps > TPS_CEILING
                || backlog < 0 || backlog > BACKLOG_CEILING) {
            throw new BadRequestException("heartbeat out of range: mspt=" + mspt + " tps=" + tps
                    + " backlog=" + backlog);
        }
        AgentStateEntity agent = agents.findById(agentId).orElseGet(() -> new AgentStateEntity(agentId));
        agent.update(mspt, tps, backlog);
        agents.save(agent);
    }

    @Override
    public double averageMspt() {
        Instant cutoff = Instant.now(clock).minus(STALE_AFTER);
        return agents.findAll().stream()
                .filter(a -> a.getReportedAt() != null && a.getReportedAt().isAfter(cutoff))
                .mapToDouble(AgentStateEntity::getMspt)
                .average().orElse(0.0);
    }
}
