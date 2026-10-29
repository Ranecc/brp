package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/** A suspected wash cycle (docs/12 §11): the score carries the accusation. */
@Entity
@Table(name = "wash_findings")
public class WashFindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tenantId;
    private LocalDate weekOf;
    private String accountsCsv;
    private String accountsHash;
    private int cycleLen;
    private int cycles;
    private double score;
    private Instant createdAt = Instant.now();

    protected WashFindingEntity() {}

    public WashFindingEntity(String tenantId, LocalDate weekOf, String accountsCsv,
                             String accountsHash, int cycleLen, int cycles, double score) {
        this.tenantId = tenantId;
        this.weekOf = weekOf;
        this.accountsCsv = accountsCsv;
        this.accountsHash = accountsHash;
        this.cycleLen = cycleLen;
        this.cycles = cycles;
        this.score = score;
    }

    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public LocalDate getWeekOf() { return weekOf; }
    public String getAccountsCsv() { return accountsCsv; }
    public String getAccountsHash() { return accountsHash; }
    public int getCycleLen() { return cycleLen; }
    public int getCycles() { return cycles; }
    public double getScore() { return score; }
    public Instant getCreatedAt() { return createdAt; }
}
