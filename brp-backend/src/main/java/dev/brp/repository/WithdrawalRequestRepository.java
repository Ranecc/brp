package dev.brp.repository;

import dev.brp.entity.WithdrawalRequestEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequestEntity, String> {
    List<WithdrawalRequestEntity> findByTeamIdAndStatusOrderById(String teamId, String status);
    List<WithdrawalRequestEntity> findTop20ByStatusOrderByIdDesc(String status);
}
