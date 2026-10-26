package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;

/**
 * Tenant-scoped idempotency key with stored response snapshot (V9: composite
 * PK (tenantId, idemKey) — the same key in two tenants is two different
 * operations, not one). The row is inserted in the SAME transaction as the
 * work it guards, so existence always implies a replayable response.
 */
@Entity
@Table(name = "idempotency_keys")
@IdClass(IdempotencyKeyEntity.Pk.class)
public class IdempotencyKeyEntity {

    public record Pk(String tenantId, String idemKey) implements Serializable {}

    @Id private String idemKey;
    @Id private String tenantId;

    private String responseJson;
    private Instant createdAt = Instant.now();

    protected IdempotencyKeyEntity() {}

    public IdempotencyKeyEntity(String tenantId, String idemKey, String responseJson) {
        this.tenantId = tenantId;
        this.idemKey = idemKey;
        this.responseJson = responseJson;
    }

    public String getIdemKey() { return idemKey; }
    public String getTenantId() { return tenantId; }
    public String getResponseJson() { return responseJson; }
}
