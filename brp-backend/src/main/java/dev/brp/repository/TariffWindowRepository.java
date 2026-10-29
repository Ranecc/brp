package dev.brp.repository;

import dev.brp.entity.TariffWindowEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TariffWindowRepository extends JpaRepository<TariffWindowEntity, Long> {

    List<TariffWindowEntity> findByVersionOrderByIdAsc(int version);

    List<TariffWindowEntity> findAllByOrderByIdAsc();
}
