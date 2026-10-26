package dev.brp.common.aspect.store;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Saga step audit row backing @Compensable (V2__players_saga_idem.sql). */
@Entity
@Table(name = "saga_instances")
public class SagaInstanceRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String saga;
    private String step;
    private String correlation;
    private String compensation;
    private String payload;
    private String state = "RUNNING";
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    protected SagaInstanceRow() {}

    public SagaInstanceRow(String saga, String step, String correlation, String compensation) {
        this.saga = saga;
        this.step = step;
        this.correlation = correlation;
        this.compensation = compensation;
    }

    public Long getId() { return id; }
    public String getSaga() { return saga; }
    public String getStep() { return step; }
    public String getCorrelation() { return correlation; }
    public String getCompensation() { return compensation; }
    public String getPayload() { return payload; }
    public String getState() { return state; }

    public void setPayload(String payload) { this.payload = payload; }

    public void done() {
        this.state = "DONE";
        this.updatedAt = Instant.now();
    }

    public void failed() {
        this.state = "FAILED";
        this.updatedAt = Instant.now();
    }

    public void compensated() {
        this.state = "COMPENSATED";
        this.updatedAt = Instant.now();
    }
}
