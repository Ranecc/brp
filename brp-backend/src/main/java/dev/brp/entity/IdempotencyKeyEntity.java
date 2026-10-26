package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Idempotency key with stored response snapshot. The row is inserted in the
 * SAME transaction as the work it guards, so its existence always implies a
 * replayable response (no in-doubt state, docs/06-transactions.md section 2).
 */
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKeyEntity {

    @Id
    private String idemKey;

    private String tenantId;
    private String responseJson;
    private Instant createdAt = Instant.now();

    protected IdempotencyKeyEntity() {}

    public IdempotencyKeyEntity(String idemKey, String tenantId, String responseJson) {
        this.idemKey = idemKey;
        this.tenantId = tenantId;
        this.responseJson = responseJson;
    }

    public String getIdemKey() { return idemKey; }
    public String getTenantId() { return tenantId; }
    public String getResponseJson() { return responseJson; }
}
