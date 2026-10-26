package dev.brp.repository;
import dev.brp.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntryEntity, Long> {
}
