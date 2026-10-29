package dev.brp.repository;
import dev.brp.entity.*;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<AccountEntity, Long> {
    Optional<AccountEntity> findByTenantIdAndAccountNo(String tenantId, String accountNo);
    List<AccountEntity> findByTenantIdOrderById(String tenantId);

    List<AccountEntity> findByAccountNoAndBalanceCentsLessThan(String accountNo, long balanceCents);
}
