package dev.brp.repository;

import dev.brp.entity.PriceObservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PriceObservationRepository extends JpaRepository<PriceObservationEntity, Long> {
    List<PriceObservationEntity> findByItemIdOrderByObservedAtAsc(String itemId);

    List<PriceObservationEntity> findTop500ByOrderByObservedAtDesc();

    List<PriceObservationEntity> findByVenue(String venue);
}
