package dev.brp.repository;
import dev.brp.entity.*;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, Long> {

    List<JournalEntryEntity> findByCreatedAtGreaterThanEqual(Instant since);

    List<JournalEntryEntity> findByTenantIdAndCreatedAtGreaterThanEqual(
            String tenantId, Instant since);

    /** Trailing income for the credit desk's auto-limit (docs/13 §6). */
    List<JournalEntryEntity> findByTenantIdAndAccountIdAndDirectionAndCurrencyAndCreatedAtGreaterThanEqual(
            String tenantId, Long accountId, String direction, String currency, Instant since);
}
