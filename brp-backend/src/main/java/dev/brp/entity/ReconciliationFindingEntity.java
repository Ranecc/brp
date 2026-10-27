package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** The reconciliation desk's output (docs/05 §3, docs/10 R-1): Σ ≠ 0, in writing. */
@Entity
@Table(name = "reconciliation_findings")
public class ReconciliationFindingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String currency;
    private long imbalanceCents;
    private String status = "OPEN";
    private Instant createdAt = Instant.now();

    protected ReconciliationFindingEntity() {}

    public ReconciliationFindingEntity(String currency, long imbalanceCents) {
        this.currency = currency;
        this.imbalanceCents = imbalanceCents;
    }

    public Long getId() { return id; }
    public String getCurrency() { return currency; }
    public long getImbalanceCents() { return imbalanceCents; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void resolve() { this.status = "RESOLVED"; }
}
