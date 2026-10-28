package dev.brp.repository;

import dev.brp.entity.WeeklySnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklySnapshotRepository extends JpaRepository<WeeklySnapshotEntity, java.time.LocalDate> {
}
