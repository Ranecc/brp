package dev.brp.repository;

import dev.brp.entity.WarehouseRowEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WarehouseRepository extends JpaRepository<WarehouseRowEntity, WarehouseRowEntity.Pk> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WarehouseRowEntity w where w.tenantId = :tenantId and w.itemId = :itemId")
    WarehouseRowEntity findForUpdate(@Param("tenantId") String tenantId,
                                     @Param("itemId") String itemId);

    List<WarehouseRowEntity> findByTenantId(String tenantId);

    List<WarehouseRowEntity> findByItemId(String itemId);
}
