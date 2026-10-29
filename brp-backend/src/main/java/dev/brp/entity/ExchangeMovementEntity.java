package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One warehouse mutation (docs/14 §8) — journal_entries discipline, item
 * edition. Append-only by convention and by red line 5's spirit: the
 * reconciliation desk sums these, so they must not lie or disappear.
 */
@Entity
@Table(name = "exchange_movements")
public class ExchangeMovementEntity {

    public static final String DEPOSIT = "DEPOSIT";
    public static final String WITHDRAW_OUT = "WITHDRAW_OUT";
    public static final String WITHDRAW_RETURN = "WITHDRAW_RETURN";
    public static final String HOLD = "HOLD";
    public static final String RELEASE = "RELEASE";
    public static final String FILL_OUT = "FILL_OUT";
    public static final String FILL_IN = "FILL_IN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;
    @Column(name = "item_id")
    private String itemId;
    @Column(name = "delta_free")
    private long deltaFree;
    @Column(name = "delta_held")
    private long deltaHeld;
    private String kind;
    @Column(name = "ref_id")
    private String refId;
    @Column(name = "idem_key")
    private String idemKey;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected ExchangeMovementEntity() {}

    public ExchangeMovementEntity(String tenantId, String itemId,
                                  long deltaFree, long deltaHeld,
                                  String kind, String refId, String idemKey) {
        this.tenantId = tenantId;
        this.itemId = itemId;
        this.deltaFree = deltaFree;
        this.deltaHeld = deltaHeld;
        this.kind = kind;
        this.refId = refId;
        this.idemKey = idemKey;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getItemId() { return itemId; }
    public long getDeltaFree() { return deltaFree; }
    public long getDeltaHeld() { return deltaHeld; }
    public String getKind() { return kind; }
    public String getRefId() { return refId; }
    public String getIdemKey() { return idemKey; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
