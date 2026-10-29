package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Vault movement (insert-only): OUT dispenses, REVOKE is a compensation. */
@Entity
@Table(name = "vault_movements")
public class VaultMovementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tenantId;
    private String containerRef;
    private String direction;
    private String itemId;
    private long qty;
    private String correlation;
    private Instant at = Instant.now();

    protected VaultMovementEntity() {}

    public VaultMovementEntity(String tenantId, String containerRef, String direction,
                               String itemId, long qty, String correlation) {
        this.tenantId = tenantId;
        this.containerRef = containerRef;
        this.direction = direction;
        this.itemId = itemId;
        this.qty = qty;
        this.correlation = correlation;
    }

    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getContainerRef() { return containerRef; }
    public String getDirection() { return direction; }
    public String getItemId() { return itemId; }
    public long getQty() { return qty; }
    public String getCorrelation() { return correlation; }
    public Instant getAt() { return at; }
}
