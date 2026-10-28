package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** A named policy regime with hysteresis state (docs/12 §4). */
@Entity
@Table(name = "policy_regimes")
public class PolicyRegimeEntity {

    public static final String ANTITRUST = "antitrust";
    public static final String ACTIVE = "ACTIVE";
    public static final String INACTIVE = "INACTIVE";

    @Id
    private String name;

    private String state;
    private Instant enteredAt;
    private double lastReading;
    private Instant updatedAt = Instant.now();

    protected PolicyRegimeEntity() {}

    public PolicyRegimeEntity(String name, String state, double lastReading) {
        this.name = name;
        this.state = state;
        this.enteredAt = Instant.now();
        this.lastReading = lastReading;
    }

    public String getName() { return name; }
    public String getState() { return state; }
    public Instant getEnteredAt() { return enteredAt; }
    public double getLastReading() { return lastReading; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void transitionTo(String next, double reading) {
        this.state = next;
        this.enteredAt = Instant.now();
        this.lastReading = reading;
        this.updatedAt = Instant.now();
    }

    public void reading(double reading) {
        this.lastReading = reading;
        this.updatedAt = Instant.now();
    }
}
