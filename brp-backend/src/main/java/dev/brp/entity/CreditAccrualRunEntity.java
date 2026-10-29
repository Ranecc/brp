package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;

/**
 * Insert-only idem guard for the daily credit job. Persistable with
 * isNew()=true forces INSERT so the duplicate-day DVE actually fires (and
 * note: Spring Data 3.5 skips delete/remove for such entities — this table
 * is insert-only in production, tests reset via a derived delete).
 */
@Entity
@Table(name = "credit_accrual_runs")
public class CreditAccrualRunEntity implements Persistable<LocalDate> {

    @Id
    private LocalDate runDate;

    protected CreditAccrualRunEntity() {}

    public CreditAccrualRunEntity(LocalDate runDate) {
        this.runDate = runDate;
    }

    public LocalDate getRunDate() { return runDate; }

    @Override public LocalDate getId() { return runDate; }

    @Override public boolean isNew() { return true; }
}
