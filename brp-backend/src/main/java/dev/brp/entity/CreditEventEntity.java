package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** Every credit action is a row: an audit trail or it didn't happen. */
@Entity
@Table(name = "credit_events")
public class CreditEventEntity {

    public static final String DRAW = "DRAW";
    public static final String REPAY = "REPAY";
    public static final String INTEREST = "INTEREST";
    public static final String LIMIT_SET = "LIMIT_SET";
    public static final String STATUS = "STATUS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id")
    private String tenantId;
    private String kind;
    @Column(name = "amount_cents")
    private long amountCents;
    private String memo;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected CreditEventEntity() {}

    public CreditEventEntity(String tenantId, String kind, long amountCents, String memo) {
        this.tenantId = tenantId;
        this.kind = kind;
        this.amountCents = amountCents;
        this.memo = memo;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getKind() { return kind; }
    public long getAmountCents() { return amountCents; }
    public String getMemo() { return memo; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
