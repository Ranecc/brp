package dev.brp.repository;

import dev.brp.entity.PolicyRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PolicyRequestRepository extends JpaRepository<PolicyRequestEntity, Long> {
    List<PolicyRequestEntity> findByStatusOrderByIdAsc(String status);
}
