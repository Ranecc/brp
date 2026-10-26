package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** One day, one currency, the observable economy (docs/03 sections 2-3). */
@Entity
@Table(name = "economy_daily")
public class EconomyDailyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDate reportDate;
    private String currency;
    private long moneyMassCents;
    private long netEmissionCents;
    private long tradedVolumeCents;
    private double velocity;

    protected EconomyDailyEntity() {}

    public EconomyDailyEntity(LocalDate reportDate, String currency, long moneyMassCents,
                              long netEmissionCents, long tradedVolumeCents, double velocity) {
        this.reportDate = reportDate;
        this.currency = currency;
        this.moneyMassCents = moneyMassCents;
        this.netEmissionCents = netEmissionCents;
        this.tradedVolumeCents = tradedVolumeCents;
        this.velocity = velocity;
    }

    public LocalDate getReportDate() { return reportDate; }
    public String getCurrency() { return currency; }
    public long getMoneyMassCents() { return moneyMassCents; }
    public long getNetEmissionCents() { return netEmissionCents; }
    public long getTradedVolumeCents() { return tradedVolumeCents; }
    public double getVelocity() { return velocity; }

    public void refresh(long moneyMassCents, long netEmissionCents,
                        long tradedVolumeCents, double velocity) {
        this.moneyMassCents = moneyMassCents;
        this.netEmissionCents = netEmissionCents;
        this.tradedVolumeCents = tradedVolumeCents;
        this.velocity = velocity;
    }
}
