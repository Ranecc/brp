package dev.brp.repository;

import dev.brp.entity.PolicyRegimeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyRegimeRepository extends JpaRepository<PolicyRegimeEntity, String> {
}
