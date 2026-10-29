package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * A withdraw in flight (docs/14 §8): the goods left the books on filing;
 * they come back on release, and only the agent's confirmation — sent after
 * the items actually spawned — closes the loop.
 */
@Entity
@Table(name = "exchange_claims")
public class ExchangeClaimEntity {

    public static final String PENDING = "PENDING";
    public static final String CONFIRMED = "CONFIRMED";
    public static final String RELEASED = "RELEASED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;
    @Column(name = "item_id")
    private String itemId;
    private long qty;
    private String status;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected ExchangeClaimEntity() {}

    public ExchangeClaimEntity(String tenantId, String itemId, long qty) {
        this.tenantId = tenantId;
        this.itemId = itemId;
        this.qty = qty;
        this.status = PENDING;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getItemId() { return itemId; }
    public long getQty() { return qty; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void close(String finalStatus) {
        this.status = finalStatus;
    }
}
