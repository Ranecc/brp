package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One resting limit order on the bourse (docs/14 §6): BIDs escrow BRC at
 * filing, ASKs hold warehouse stock. Unfilled remainders persist to the
 * next batch — the batch auction has no takers, only batches.
 */
@Entity
@Table(name = "exchange_orders")
public class ExchangeOrderEntity {

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
    @Column(name = "item_id")
    private String itemId;
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

    protected ExchangeOrderEntity() {}

    public ExchangeOrderEntity(String tenantId, String itemId, String idemKey,
                               String side, long qty, long priceCents,
                               OffsetDateTime expiresAt) {
        this.tenantId = tenantId;
        this.itemId = itemId;
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
    public String getItemId() { return itemId; }
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

    /** @return the quantity filled by this allocation. */
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
