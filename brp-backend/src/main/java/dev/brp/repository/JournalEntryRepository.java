package dev.brp.repository;
import dev.brp.entity.*;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, Long> {

    List<JournalEntryEntity> findByCreatedAtGreaterThanEqual(Instant since);

    List<JournalEntryEntity> findByTenantIdAndCreatedAtGreaterThanEqual(
            String tenantId, Instant since);
}
