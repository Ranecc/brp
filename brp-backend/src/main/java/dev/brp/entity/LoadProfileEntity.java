package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Durable minute-of-day seasonality (docs/13 §2): EWMA of mspt per minute of
 * day, refreshed once daily. sample_days counts contributing days — below the
 * cold-start threshold the forecaster reports honestly instead of inventing.
 */
@Entity
@Table(name = "load_profile")
public class LoadProfileEntity {

    @Id
    private int minuteOfDay;

    private double ewmaMspt;
    private int sampleDays;

    protected LoadProfileEntity() {}

    public LoadProfileEntity(int minuteOfDay, double todayMspt) {
        this.minuteOfDay = minuteOfDay;
        this.ewmaMspt = todayMspt;
        this.sampleDays = 1;
    }

    public int getMinuteOfDay() { return minuteOfDay; }
    public double getEwmaMspt() { return ewmaMspt; }
    public int getSampleDays() { return sampleDays; }

    /** Daily EWMA update; alpha lives with the caller (config-owned). */
    public void blend(double todayMspt, double alpha) {
        this.ewmaMspt = this.ewmaMspt * (1 - alpha) + todayMspt * alpha;
        this.sampleDays++;
    }
}
