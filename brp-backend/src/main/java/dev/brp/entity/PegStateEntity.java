package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One peg leg (docs/12 §9): rate + band; the band absorbs +/-5% drift. */
@Entity
@Table(name = "peg_state")
public class PegStateEntity {

    public static final String EMERALD = "emerald";
    public static final String QUOTA = "quota";

    @Id
    private String leg;

    private double rate;
    private double bandLow;
    private double bandHigh;
    private int outOfBandStreak;
    private Instant updatedAt = Instant.now();

    protected PegStateEntity() {}

    public PegStateEntity(String leg, double rate, double bandPct) {
        this.leg = leg;
        this.rate = rate;
        this.bandLow = rate * (1 - bandPct);
        this.bandHigh = rate * (1 + bandPct);
    }

    public String getLeg() { return leg; }
    public double getRate() { return rate; }
    public double getBandLow() { return bandLow; }
    public double getBandHigh() { return bandHigh; }
    public int getOutOfBandStreak() { return outOfBandStreak; }
    public Instant getUpdatedAt() { return updatedAt; }

    public boolean outOfBand(double observed) {
        return observed < bandLow || observed > bandHigh;
    }

    public void streak(int n) { this.outOfBandStreak = n; this.updatedAt = Instant.now(); }

    public void repeg(double newRate, double bandPct) {
        this.rate = newRate;
        this.bandLow = newRate * (1 - bandPct);
        this.bandHigh = newRate * (1 + bandPct);
        this.outOfBandStreak = 0;
        this.updatedAt = Instant.now();
    }
}
