package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row of the versioned time-of-use tariff (docs/13 §4). Version 0 is
 * boot-config-owned; versions 1+ are API-owned. Resolution takes the highest
 * version present, first matching row, default 100%.
 */
@Entity
@Table(name = "tariff_windows")
public class TariffWindowEntity {

    public static final int ALL_DAYS = -1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int version;
    private int dayOfWeek;
    private int startMinute;
    private int endMinute;
    @Column(name = "multiplier_pct")
    private int multiplierPct;
    private String label;

    protected TariffWindowEntity() {}

    public TariffWindowEntity(int version, int dayOfWeek, int startMinute,
                              int endMinute, int multiplierPct, String label) {
        this.version = version;
        this.dayOfWeek = dayOfWeek;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.multiplierPct = multiplierPct;
        this.label = label;
    }

    public Long getId() { return id; }
    public int getVersion() { return version; }
    public int getDayOfWeek() { return dayOfWeek; }
    public int getStartMinute() { return startMinute; }
    public int getEndMinute() { return endMinute; }
    public int getMultiplierPct() { return multiplierPct; }
    public String getLabel() { return label; }
}
