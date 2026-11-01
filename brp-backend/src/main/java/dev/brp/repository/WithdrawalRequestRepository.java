package dev.brp.repository;

import dev.brp.entity.WithdrawalRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequestEntity, String> {
    List<WithdrawalRequestEntity> findByTeamIdAndStatusOrderByCreatedAtDesc(String teamId, String status);
    List<WithdrawalRequestEntity> findTop20ByStatusOrderByCreatedAtDesc(String status);
}
