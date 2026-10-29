package dev.brp.repository;

import dev.brp.entity.ProfileRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

public interface ProfileRunRepository extends JpaRepository<ProfileRunEntity, LocalDate> {

    /**
     * JPQL bulk delete — the ONLY delete that works on Persistable-true
     * entities in Spring Data 3.5 (deleteAll/derived deletes skip them as
     * "new"). Production never deletes; tests reset the day's guard here.
     */
    @Modifying
    @Transactional
    @Query("delete from ProfileRunEntity r where r.refreshDate = :date")
    void deleteGuard(@Param("date") LocalDate date);
}
