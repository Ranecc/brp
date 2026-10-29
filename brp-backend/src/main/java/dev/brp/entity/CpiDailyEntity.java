package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** The day's CPI print (docs/12 §12): base week = 100. One truth per day. */
@Entity
@Table(name = "cpi_daily")
public class CpiDailyEntity {

    @Id @Column(name = "day_stamp")
    private LocalDate day;

    private double indexValue;
    private String basketJson;
    private Instant capturedAt = Instant.now();

    protected CpiDailyEntity() {}

    public CpiDailyEntity(LocalDate day, double indexValue, String basketJson) {
        this.day = day;
        this.indexValue = indexValue;
        this.basketJson = basketJson;
    }

    public LocalDate getDay() { return day; }
    public double getIndexValue() { return indexValue; }
    public String getBasketJson() { return basketJson; }
    public Instant getCapturedAt() { return capturedAt; }
}
