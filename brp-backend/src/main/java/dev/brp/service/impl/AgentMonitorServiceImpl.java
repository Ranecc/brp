package dev.brp.service.impl;

import dev.brp.common.annotation.Atomic;
import dev.brp.entity.AgentStateEntity;
import dev.brp.repository.AgentStateRepository;
import dev.brp.service.AgentMonitorService;
import org.springframework.stereotype.Service;

/**
 * Latest-known-state per agent. Stale rows age out of the average once we
 * grow a real fleet (P1 sweeper); today the fleet is small and honest.
 */
@Service
public class AgentMonitorServiceImpl implements AgentMonitorService {

    private final AgentStateRepository agents;

    public AgentMonitorServiceImpl(AgentStateRepository agents) {
        this.agents = agents;
    }

    @Override
    @Atomic
    public void recordHeartbeat(String agentId, double mspt, double tps, int backlog) {
        AgentStateEntity agent = agents.findById(agentId).orElseGet(() -> new AgentStateEntity(agentId));
        agent.update(mspt, tps, backlog);
        agents.save(agent);
    }

    @Override
    public double averageMspt() {
        return agents.findAll().stream().mapToDouble(AgentStateEntity::getMspt).average().orElse(0.0);
    }
}
