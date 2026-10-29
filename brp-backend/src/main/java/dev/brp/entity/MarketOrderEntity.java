package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One resting order (docs/13 §8): ASKs lock quota capacity (the market_quota
 * bucket's used counter), BIDs lock BRC escrow. No margin, no shorting.
 */
@Entity
@Table(name = "market_orders")
public class MarketOrderEntity {

    public static final String ASK = "ASK";
    public static final String BID = "BID";

    public static final String NEW = "NEW";
    public static final String PARTIAL = "PARTIAL";
    public static final String FILLED = "FILLED";
    public static final String CANCELLED = "CANCELLED";
    public static final String EXPIRED = "EXPIRED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;
    @Column(name = "idem_key")
    private String idemKey;
    private String side;
    private long qty;
    @Column(name = "qty_remaining")
    private long qtyRemaining;
    @Column(name = "price_cents")
    private long priceCents;
    private String status;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    protected MarketOrderEntity() {}

    public MarketOrderEntity(String tenantId, String idemKey, String side,
                             long qty, long priceCents, OffsetDateTime expiresAt) {
        this.tenantId = tenantId;
        this.idemKey = idemKey;
        this.side = side;
        this.qty = qty;
        this.qtyRemaining = qty;
        this.priceCents = priceCents;
        this.status = NEW;
        this.createdAt = OffsetDateTime.now();
        this.expiresAt = expiresAt;
    }

    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getIdemKey() { return idemKey; }
    public String getSide() { return side; }
    public long getQty() { return qty; }
    public long getQtyRemaining() { return qtyRemaining; }
    public long getPriceCents() { return priceCents; }
    public String getStatus() { return status; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }

    public boolean resting() {
        return NEW.equals(status) || PARTIAL.equals(status);
    }

    /** @return the quantity filled by this cross. */
    public long fill(long quantity) {
        long taken = Math.min(quantity, qtyRemaining);
        this.qtyRemaining -= taken;
        this.status = qtyRemaining == 0 ? FILLED : PARTIAL;
        return taken;
    }

    public void close(String finalStatus) {
        this.status = finalStatus;
    }
}
