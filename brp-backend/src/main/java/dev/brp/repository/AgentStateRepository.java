package dev.brp.repository;

import dev.brp.entity.AgentStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentStateRepository extends JpaRepository<AgentStateEntity, String> {}
