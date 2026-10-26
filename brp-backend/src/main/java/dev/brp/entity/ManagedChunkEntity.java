package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;

/**
 * A tenant-registered chunk slot (docs/09 section 3). The governor reads
 * (status, creditMinutes) and rules; HALT stops BRP machinery, never
 * player movement.
 */
@Entity
@Table(name = "managed_chunks")
@IdClass(ManagedChunkEntity.Pk.class)
public class ManagedChunkEntity {

    public record Pk(String tenantId, String dimension, String chunkKey)
            implements Serializable {}

    @Id private String tenantId;
    @Id private String dimension;
    @Id private String chunkKey;

    private String status;
    private long creditMinutes;
    private String registeredBy;
    private Instant registeredAt;

    protected ManagedChunkEntity() {}

    public ManagedChunkEntity(String tenantId, String dimension, String chunkKey,
                              String registeredBy) {
        this.tenantId = tenantId;
        this.dimension = dimension;
        this.chunkKey = chunkKey;
        this.status = ChunkStatus.ACTIVE;
        this.creditMinutes = 0;
        this.registeredBy = registeredBy;
        this.registeredAt = Instant.now();
    }

    /** Chunk status lifecycle: ACTIVE <-> DISABLED by the admin remote switch. */
    public static final class ChunkStatus {
        public static final String ACTIVE = "ACTIVE";
        public static final String DISABLED = "DISABLED";
        private ChunkStatus() {}
    }

    public String getTenantId() { return tenantId; }
    public String getDimension() { return dimension; }
    public String getChunkKey() { return chunkKey; }
    public String getStatus() { return status; }
    public long getCreditMinutes() { return creditMinutes; }
    public String getRegisteredBy() { return registeredBy; }
    public Instant getRegisteredAt() { return registeredAt; }

    public void setStatus(String status) { this.status = status; }

    /** Metering/billing moves credit; P2-04 decrements, grants increment. */
    public void setCreditMinutes(long creditMinutes) { this.creditMinutes = creditMinutes; }
}
