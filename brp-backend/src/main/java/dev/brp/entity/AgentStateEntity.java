package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Latest-known agent heartbeat; upserted per agent on every accepted heartbeat. */
@Entity
@Table(name = "agent_state")
public class AgentStateEntity {

    @Id
    private String agentId;

    private double mspt;
    private double tps;
    private int backlog;
    private Instant reportedAt = Instant.now();

    protected AgentStateEntity() {}

    public AgentStateEntity(String agentId) {
        this.agentId = agentId;
    }

    public String getAgentId() { return agentId; }
    public double getMspt() { return mspt; }
    public double getTps() { return tps; }
    public int getBacklog() { return backlog; }
    public Instant getReportedAt() { return reportedAt; }

    public void update(double mspt, double tps, int backlog) {
        this.mspt = mspt;
        this.tps = tps;
        this.backlog = backlog;
        this.reportedAt = Instant.now();
    }
}
