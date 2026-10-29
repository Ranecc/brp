package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;

/**
 * Insert-only idem guard: the daily profile refresh runs once per date.
 * Persistable with isNew()=true forces INSERT — with the id set in the
 * constructor, save() would silently merge and the duplicate-day guard
 * would never fire (the same merge pitfall the chunk purchase keys hit).
 */
@Entity
@Table(name = "load_profile_runs")
public class ProfileRunEntity implements Persistable<LocalDate> {

    @Id
    private LocalDate refreshDate;

    protected ProfileRunEntity() {}

    public ProfileRunEntity(LocalDate refreshDate) {
        this.refreshDate = refreshDate;
    }

    public LocalDate getRefreshDate() { return refreshDate; }

    @Override public LocalDate getId() { return refreshDate; }

    @Override public boolean isNew() { return true; }
}
