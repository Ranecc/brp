package dev.brp.inventory;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "inventory_snapshots",
       uniqueConstraints = @UniqueConstraint(name = "uq_inv",
               columnNames = {"tenantId", "containerId", "itemId"}))
public class InventorySnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tenantId;
    private String containerId;
    private String itemId;
    private long amount;
    private Instant capturedAt = Instant.now();

    protected InventorySnapshotEntity() {}

    public InventorySnapshotEntity(String tenantId, String containerId, String itemId, long amount) {
        this.tenantId = tenantId;
        this.containerId = containerId;
        this.itemId = itemId;
        this.amount = amount;
    }

    public String getTenantId() { return tenantId; }
    public String getContainerId() { return containerId; }
    public String getItemId() { return itemId; }
    public long getAmount() { return amount; }
    public Instant getCapturedAt() { return capturedAt; }

    void setAmount(long amount) { this.amount = amount; }
    void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }
}
