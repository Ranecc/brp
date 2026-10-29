package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;

/** A registered vault container (docs/12 §8): the physical half of bimetallism. */
@Entity
@Table(name = "vault_registrations")
@IdClass(VaultRegistrationEntity.Pk.class)
public class VaultRegistrationEntity {

    public record Pk(String tenantId, String containerRef) implements Serializable {}

    @Id private String tenantId;
    @Id private String containerRef;

    private String itemId;
    private Instant registeredAt = Instant.now();

    protected VaultRegistrationEntity() {}

    public VaultRegistrationEntity(String tenantId, String containerRef, String itemId) {
        this.tenantId = tenantId;
        this.containerRef = containerRef;
        this.itemId = itemId;
    }

    public String getTenantId() { return tenantId; }
    public String getContainerRef() { return containerRef; }
    public String getItemId() { return itemId; }
    public Instant getRegisteredAt() { return registeredAt; }
}
