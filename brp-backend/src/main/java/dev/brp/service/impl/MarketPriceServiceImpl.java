package dev.brp.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.brp.common.BadRequestException;
import dev.brp.entity.CpiDailyEntity;
import dev.brp.entity.PriceObservationEntity;
import dev.brp.repository.CpiDailyRepository;
import dev.brp.repository.PriceObservationRepository;
import dev.brp.service.MarketPriceService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * CPI (docs/12 §12): relative = latest price / first observed price per
 * item; weights from brp.cpi.basket (item -> weight), empty basket = equal
 * weights. Index = 100 * prod(relative^weight). One print per day.
 */
@Service
public class MarketPriceServiceImpl implements MarketPriceService {

    private final PriceObservationRepository observations;
    private final CpiDailyRepository daily;
    private final ObjectMapper mapper;
    private final String basketJson;
    private final ZoneId zone;
    private Map<String, Double> basket = Map.of();

    public MarketPriceServiceImpl(PriceObservationRepository observations,
                                  CpiDailyRepository daily, ObjectMapper mapper,
                                  @Value("${brp.cpi.basket:}") String basketJson,
                                  @Value("${brp.reports.zone:UTC}") String zoneId) {
        this.observations = observations;
        this.daily = daily;
        this.mapper = mapper;
        this.basketJson = basketJson;
        this.zone = ZoneId.of(zoneId);
    }

    @PostConstruct
    void loadBasket() {
        if (basketJson == null || basketJson.isBlank()) {
            basket = Map.of();
            return;
        }
        try {
            basket = mapper.readValue(basketJson, new TypeReference<Map<String, Double>>() {});
        } catch (Exception bad) {
            throw new IllegalStateException("brp.cpi.basket is not a JSON object", bad);
        }
    }

    @Override
    public void record(String itemId, long priceCents, String currency, long qty,
                       String venue, String source) {
        if (itemId == null || itemId.isBlank() || priceCents <= 0) {
            throw new BadRequestException("monetary.price.invalid");
        }
        observations.save(new PriceObservationEntity(itemId, priceCents,
                currency == null || currency.isBlank() ? "BRC" : currency,
                Math.max(1, qty), venue, source == null ? "manual" : source));
    }

    @Override
    public double materializeToday() {
        LocalDate today = LocalDate.now(zone);
        if (daily.existsById(today)) {
            return daily.findById(today).orElseThrow().getIndexValue();   // one truth per day
        }
        Map<String, List<PriceObservationEntity>> byItem = new HashMap<>();
        for (PriceObservationEntity o : observations.findTop500ByOrderByObservedAtDesc()) {
            byItem.computeIfAbsent(o.getItemId(), k -> new ArrayList<>()).add(o);
        }
        Map<String, Double> relatives = new TreeMap<>();
        for (var entry : byItem.entrySet()) {
            List<PriceObservationEntity> list = entry.getValue();
            list.sort(java.util.Comparator.comparing(PriceObservationEntity::getObservedAt).reversed());
            long latest = list.get(0).getPriceCents();
            long earliest = list.get(list.size() - 1).getPriceCents();
            if (earliest > 0 && !list.get(0).getObservedAt().equals(list.get(list.size() - 1).getObservedAt())) {
                relatives.put(entry.getKey(), (double) latest / earliest);
            }
        }
        if (relatives.isEmpty()) {
            throw new dev.brp.common.ConflictException("monetary.cpi.empty");
        }
        Map<String, Double> weights = effectiveWeights(relatives.keySet());
        double index = 100.0;
        for (var entry : weights.entrySet()) {
            index *= Math.pow(relatives.get(entry.getKey()), entry.getValue());
        }
        index = Math.round(index * 100.0) / 100.0;
        try {
            daily.saveAndFlush(new CpiDailyEntity(today, index,
                    relatives.isEmpty() ? "{}" : mapper.writeValueAsString(relatives)));
        } catch (Exception jsonNever) {
            daily.save(new CpiDailyEntity(today, index, "{}"));
        }
        return index;
    }

    /** Configured basket when it covers observed items; equal weights otherwise. */
    private Map<String, Double> effectiveWeights(java.util.Set<String> items) {
        Map<String, Double> out = new HashMap<>();
        if (basket.isEmpty()) {
            for (String item : items) {
                out.put(item, 1.0 / items.size());
            }
            return out;
        }
        double total = basket.values().stream().mapToDouble(Double::doubleValue).sum();
        for (String item : items) {
            out.put(item, basket.getOrDefault(item, 0.0) / (total <= 0 ? 1 : total));
        }
        return out;
    }

    @Override
    public List<CpiDailyEntity> history() {
        return daily.findTop90ByOrderByDayDesc();
    }

    @Override
    public Map<String, Double> shares() {
        Map<String, Double> out = new TreeMap<>();
        observations.findTop500ByOrderByObservedAtDesc().stream()
                .collect(java.util.stream.Collectors.groupingBy(PriceObservationEntity::getItemId,
                        java.util.stream.Collectors.summingLong(PriceObservationEntity::getPriceCents)))
                .forEach((item, sum) -> out.put(item, (double) sum));
        return out;
    }

    /** Daily, shortly after the books open. */
    @Scheduled(cron = "${brp.cpi.cron:0 30 0 * * *}", zone = "${brp.reports.zone:UTC}")
    public void scheduled() {
        try {
            materializeToday();
        } catch (RuntimeException notEnoughDataYet) {
            // an index needs at least one moving price; silence is honest
        }
    }
}
