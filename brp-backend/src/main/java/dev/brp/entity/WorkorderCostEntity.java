package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * Completion cost stamped at handover (docs/13 §9). Persistable with
 * isNew()=true: the stamp is write-once — a restamp would be revisionism.
 */
@Entity
@Table(name = "workorder_costs")
@IdClass(WorkorderCostEntity.Pk.class)
public class WorkorderCostEntity implements Persistable<WorkorderCostEntity.Pk> {

    public record Pk(String tenantId, String workorderRef) implements Serializable {}

    @Id @Column(name = "tenant_id") private String tenantId;
    @Id @Column(name = "workorder_ref") private String workorderRef;

    @Column(name = "material_cost_cents")
    private long materialCostCents;
    @Column(name = "machine_cost_cents")
    private long machineCostCents;
    @Column(name = "stamped_at")
    private OffsetDateTime stampedAt;

    protected WorkorderCostEntity() {}

    public WorkorderCostEntity(String tenantId, String workorderRef,
                               long materialCostCents, long machineCostCents) {
        this.tenantId = tenantId;
        this.workorderRef = workorderRef;
        this.materialCostCents = materialCostCents;
        this.machineCostCents = machineCostCents;
        this.stampedAt = OffsetDateTime.now();
    }

    public String getTenantId() { return tenantId; }
    public String getWorkorderRef() { return workorderRef; }
    public long getMaterialCostCents() { return materialCostCents; }
    public long getMachineCostCents() { return machineCostCents; }
    public OffsetDateTime getStampedAt() { return stampedAt; }

    @Override public Pk getId() { return new Pk(tenantId, workorderRef); }

    @Override public boolean isNew() { return true; }
}
