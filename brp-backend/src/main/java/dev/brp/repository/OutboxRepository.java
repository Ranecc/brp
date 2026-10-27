package dev.brp.repository;

import dev.brp.entity.OutboxMessageEntity;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OutboxRepository extends JpaRepository<OutboxMessageEntity, Long> {

    List<OutboxMessageEntity> findTop20ByStatusOrderByIdAsc(String status);

    Optional<OutboxMessageEntity> findFirstByStatusOrderByIdAsc(String status);

    /**
     * Atomic claim (audit #2): PENDING -> SENDING only if still PENDING.
     * Two dispatchers race; the loser's update matches zero rows and walks on.
     */
    @Transactional
    @Modifying
    @Query("update OutboxMessageEntity m set m.status = 'SENDING', m.updatedAt = :now"
            + " where m.id = :id and m.status = 'PENDING'")
    int claim(@Param("id") Long id, @Param("now") Instant now);

    /** Sweeper: FAILED -> PENDING while attempts remain (backoff via updated_at). */
    @Transactional
    @Modifying
    @Query("update OutboxMessageEntity m set m.status = 'PENDING', m.updatedAt = :now"
            + " where m.status = 'FAILED' and m.attempts < :maxAttempts"
            + " and m.updatedAt <= :before")
    int requeueFailed(@Param("maxAttempts") int maxAttempts,
                      @Param("before") Instant before,
                      @Param("now") Instant now);
}
