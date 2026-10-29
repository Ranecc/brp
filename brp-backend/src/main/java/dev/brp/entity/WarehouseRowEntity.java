package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;

/**
 * Materialized custody (docs/14 §6): the movement log is the truth, this row
 * is its running sum. qty_held answers to resting ASKs, nothing else.
 */
@Entity
@Table(name = "warehouse")
@IdClass(WarehouseRowEntity.Pk.class)
public class WarehouseRowEntity {

    public record Pk(String tenantId, String itemId) implements Serializable {}

    @Id private String tenantId;
    @Id private String itemId;

    private long qtyFree;
    private long qtyHeld;

    protected WarehouseRowEntity() {}

    public WarehouseRowEntity(String tenantId, String itemId) {
        this.tenantId = tenantId;
        this.itemId = itemId;
    }

    public String getTenantId() { return tenantId; }
    public String getItemId() { return itemId; }
    public long getQtyFree() { return qtyFree; }
    public long getQtyHeld() { return qtyHeld; }

    public void setQtyFree(long qtyFree) { this.qtyFree = qtyFree; }
    public void setQtyHeld(long qtyHeld) { this.qtyHeld = qtyHeld; }

    public void grow(long deltaFree, long deltaHeld) {
        this.qtyFree += deltaFree;
        this.qtyHeld += deltaHeld;
    }
}
