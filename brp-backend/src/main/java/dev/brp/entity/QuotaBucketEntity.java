package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.IdClass;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.io.Serializable;

/** Per-tenant resource quota: limit 0 means unlimited licensing. */
@Entity
@Table(name = "quota_buckets")
@IdClass(QuotaBucketEntity.Pk.class)
public class QuotaBucketEntity {

    public record Pk(String tenantId, String resource) implements Serializable {}

    @Id private String tenantId;
    @Id private String resource;

    private long limitAmount;
    private long used;

    protected QuotaBucketEntity() {}

    public QuotaBucketEntity(String tenantId, String resource, long limitAmount) {
        this.tenantId = tenantId;
        this.resource = resource;
        this.limitAmount = limitAmount;
    }

    public String getTenantId() { return tenantId; }
    public String getResource() { return resource; }
    public long getLimitAmount() { return limitAmount; }
    public long getUsed() { return used; }

    public void setLimitAmount(long limitAmount) { this.limitAmount = limitAmount; }

    /** @return false when the placement exceeds the license. */
    public boolean tryConsumeOne() {
        if (limitAmount > 0 && used >= limitAmount) {
            return false;
        }
        used++;
        return true;
    }
}
