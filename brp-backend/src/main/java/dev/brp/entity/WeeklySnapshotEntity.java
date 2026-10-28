package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** One shared weekly snapshot (docs/12 §3): Gini, velocity, top share. */
@Entity
@Table(name = "weekly_snapshots")
public class WeeklySnapshotEntity {

    @Id
    private LocalDate weekOf;

    private double gini;
    private double topShare;
    private String regimeAfter;
    private Instant capturedAt = Instant.now();

    protected WeeklySnapshotEntity() {}

    public WeeklySnapshotEntity(LocalDate weekOf, double gini, double topShare, String regimeAfter) {
        this.weekOf = weekOf;
        this.gini = gini;
        this.topShare = topShare;
        this.regimeAfter = regimeAfter;
    }

    public LocalDate getWeekOf() { return weekOf; }
    public double getGini() { return gini; }
    public double getTopShare() { return topShare; }
    public String getRegimeAfter() { return regimeAfter; }
    public Instant getCapturedAt() { return capturedAt; }
}
