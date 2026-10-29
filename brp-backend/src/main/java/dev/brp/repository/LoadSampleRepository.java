package dev.brp.repository;

import dev.brp.entity.LoadSampleEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface LoadSampleRepository extends JpaRepository<LoadSampleEntity, LocalDateTime> {

    /** Row-locked upsert: concurrent heartbeats serialize on the minute. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from LoadSampleEntity s where s.bucketMinute = :minute")
    LoadSampleEntity findForUpdate(@Param("minute") LocalDateTime minute);

    List<LoadSampleEntity> findByBucketMinuteGreaterThanEqualOrderByBucketMinuteAsc(LocalDateTime since);

    List<LoadSampleEntity> findByBucketMinuteBetweenOrderByBucketMinuteAsc(LocalDateTime from, LocalDateTime to);

    @Transactional
    void deleteByBucketMinuteBefore(LocalDateTime cutoff);
}
