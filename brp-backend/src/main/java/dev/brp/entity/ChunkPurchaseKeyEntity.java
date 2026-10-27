package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;

/**
 * Purchase idempotency for chunk credits: one key, one grant of minutes.
 * Persistable with isNew()=true forces INSERT on every save — a replay must
 * hit the unique constraint, never silently merge like it owns the place.
 */
@Entity
@Table(name = "chunk_purchase_keys")
@IdClass(ChunkPurchaseKeyEntity.Pk.class)
public class ChunkPurchaseKeyEntity implements Persistable<ChunkPurchaseKeyEntity.Pk> {

    public record Pk(String tenantId, String idemKey) implements Serializable {}

    @Id private String tenantId;
    @Id private String idemKey;

    protected ChunkPurchaseKeyEntity() {}

    public ChunkPurchaseKeyEntity(String tenantId, String idemKey) {
        this.tenantId = tenantId;
        this.idemKey = idemKey;
    }

    @Override
    public boolean isNew() {
        return true;   // insert-only by design
    }

    @Override
    public Pk getId() {
        return new Pk(tenantId, idemKey);
    }
}
