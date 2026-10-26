package dev.brp.service;

/** Agent heartbeat aggregation (impl: {@link dev.brp.service.impl.AgentMonitorServiceImpl}). */
public interface AgentMonitorService {

    /** Upsert latest-known state for one agent (idempotent per heartbeat). */
    void recordHeartbeat(String agentId, double mspt, double tps, int backlog);

    /** Fleet mean mspt across reporting agents; 0 when nobody is talking. */
    double averageMspt();
}
