package dev.brp.repository;
import dev.brp.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlayerRepository extends JpaRepository<PlayerEntity, String> {}
