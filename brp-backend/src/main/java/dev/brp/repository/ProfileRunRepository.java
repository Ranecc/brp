package dev.brp.repository;

import dev.brp.entity.ProfileRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

public interface ProfileRunRepository extends JpaRepository<ProfileRunEntity, LocalDate> {

    /**
     * Derived bulk delete — the only way to remove these rows. SimpleJpaRepository's
     * deleteAll/deleteById skip entities whose Persistable.isNew() is true, which
     * ProfileRunEntity is by construction; the guard is insert-only in production
     * (tests reset the day's row through this method).
     */
    @Transactional
    void deleteByRefreshDate(LocalDate refreshDate);
}
