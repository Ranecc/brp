package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;

/** Wealth levy assessment per (week, tenant) — insert-only idem (docs/12 §6). */
@Entity
@Table(name = "wealth_assessments")
@IdClass(WealthAssessmentEntity.Pk.class)
public class WealthAssessmentEntity {

    public record Pk(String weekOf, String tenantId) implements Serializable {}

    @Id private String weekOf;
    @Id private String tenantId;

    private long bracketCents;
    private boolean surcharge;
    private long amountCents;
    private boolean posted;

    protected WealthAssessmentEntity() {}

    public WealthAssessmentEntity(String weekOf, String tenantId, long bracketCents,
                                  boolean surcharge, long amountCents, boolean posted) {
        this.weekOf = weekOf;
        this.tenantId = tenantId;
        this.bracketCents = bracketCents;
        this.surcharge = surcharge;
        this.amountCents = amountCents;
        this.posted = posted;
    }

    public String getWeekOf() { return weekOf; }
    public String getTenantId() { return tenantId; }
    public long getBracketCents() { return bracketCents; }
    public boolean isSurcharge() { return surcharge; }
    public long getAmountCents() { return amountCents; }
    public boolean isPosted() { return posted; }
}
