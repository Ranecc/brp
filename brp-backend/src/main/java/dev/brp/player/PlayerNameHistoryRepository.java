package dev.brp.player;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerNameHistoryRepository extends JpaRepository<PlayerNameHistoryRow, Long> {}
