package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** A handover result (docs/13 §9): cost basis kept, depreciation deferred to P5's proposal box. */
@Entity
@Table(name = "assets")
public class AssetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;
    private String kind;
    private String label;
    @Column(name = "location_chunk")
    private String locationChunk;
    @Column(name = "cost_basis_cents")
    private long costBasisCents;
    private String status;
    @Column(name = "acquired_at")
    private OffsetDateTime acquiredAt;

    protected AssetEntity() {}

    public AssetEntity(String tenantId, String kind, String label,
                       String locationChunk, long costBasisCents) {
        this.tenantId = tenantId;
        this.kind = kind;
        this.label = label;
        this.locationChunk = locationChunk;
        this.costBasisCents = costBasisCents;
        this.status = "IN_SERVICE";
        this.acquiredAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getKind() { return kind; }
    public String getLabel() { return label; }
    public String getLocationChunk() { return locationChunk; }
    public long getCostBasisCents() { return costBasisCents; }
    public String getStatus() { return status; }
    public OffsetDateTime getAcquiredAt() { return acquiredAt; }

    public void retire() { this.status = "RETIRED"; }
}
