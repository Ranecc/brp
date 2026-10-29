package dev.brp.repository;

import dev.brp.entity.LevyMinuteEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LevyMinuteRepository extends JpaRepository<LevyMinuteEntity, LevyMinuteEntity.Pk> {

    /** Row-locked accumulation: telemetry events and the materializer serialize. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from LevyMinuteEntity l where l.minuteBucket = :minute and l.tenantId = :tenant")
    LevyMinuteEntity findForUpdate(@Param("minute") LocalDateTime minute,
                                   @Param("tenant") String tenantId);

    List<LevyMinuteEntity> findByMinuteBucket(LocalDateTime minuteBucket);

    List<LevyMinuteEntity> findByMinuteBucketBetween(LocalDateTime from, LocalDateTime to);
}
