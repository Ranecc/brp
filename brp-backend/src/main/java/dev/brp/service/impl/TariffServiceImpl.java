package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.dto.LoadDtos.AddTariffWindowRequest;
import dev.brp.dto.LoadDtos.TariffWindowResponse;
import dev.brp.entity.TariffWindowEntity;
import dev.brp.repository.TariffWindowRepository;
import dev.brp.service.TariffService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class TariffServiceImpl implements TariffService {

    static final String DEFAULT_LABEL = "SHOULDER";
    private static final int PAR = 100;

    private final TariffWindowRepository rows;
    private final ZoneId zone;
    private final int seedStartHour;
    private final int seedEndHour;
    private final int seedSurchargePercent;

    public TariffServiceImpl(TariffWindowRepository rows,
                             @Value("${brp.reports.zone:UTC}") String zoneId,
                             @Value("${brp.chunk.billing.peak-start-hour:19}") int seedStartHour,
                             @Value("${brp.chunk.billing.peak-end-hour:23}") int seedEndHour,
                             @Value("${brp.chunk.billing.peak-surcharge-percent:0}") int seedSurchargePercent) {
        this.rows = rows;
        this.zone = ZoneId.of(zoneId);
        this.seedStartHour = seedStartHour;
        this.seedEndHour = seedEndHour;
        this.seedSurchargePercent = seedSurchargePercent;
    }

    /**
     * Version 0 belongs to the boot config: re-derived on every start so the
     * legacy peak-surcharge keys keep meaning something until the console
     * publishes version 1. API-owned versions are never touched here.
     */
    @PostConstruct
    void seedVersionZero() {
        List<TariffWindowEntity> v0 = rows.findByVersionOrderByIdAsc(0);
        int wantPct = PAR + seedSurchargePercent;
        int wantStart = seedStartHour * 60;
        int wantEnd = seedEndHour * 60;
        if (v0.size() == 1
                && v0.get(0).getMultiplierPct() == wantPct
                && v0.get(0).getStartMinute() == wantStart
                && v0.get(0).getEndMinute() == wantEnd) {
            return;
        }
        rows.deleteAll(v0);
        rows.save(new TariffWindowEntity(0, TariffWindowEntity.ALL_DAYS,
                wantStart, wantEnd, wantPct, "PEAK"));
        rows.flush();
    }

    @Override
    public int multiplierPctAt(Instant when) {
        return resolveAt(when).multiplierPct();
    }

    @Override
    public Resolution resolveAt(Instant when) {
        List<TariffWindowEntity> all = rows.findAllByOrderByIdAsc();
        return TariffMath.resolve(all, LocalDateTime.ofInstant(when, zone));
    }

    @Override
    public List<TariffWindowResponse> windows() {
        return rows.findAllByOrderByIdAsc().stream().map(TariffServiceImpl::toDto).toList();
    }

    @Override
    public TariffWindowResponse addWindow(AddTariffWindowRequest req) {
        validate(req);
        int latest = rows.findAllByOrderByIdAsc().stream()
                .mapToInt(TariffWindowEntity::getVersion).max().orElse(0);
        int next = latest + 1;
        List<TariffWindowEntity> carried = rows.findByVersionOrderByIdAsc(latest).stream()
                .map(w -> new TariffWindowEntity(next, w.getDayOfWeek(), w.getStartMinute(),
                        w.getEndMinute(), w.getMultiplierPct(), w.getLabel()))
                .collect(java.util.stream.Collectors.toList());
        carried.add(new TariffWindowEntity(next, req.dayOfWeek(), req.startMinute(),
                req.endMinute(), req.multiplierPct(), req.label()));
        return toDto(rows.save(carried.get(carried.size() - 1)));
    }

    private static void validate(AddTariffWindowRequest req) {
        if (req.dayOfWeek() < -1 || req.dayOfWeek() > 7) {
            throw new BadRequestException("tariff.day.invalid");
        }
        if (req.startMinute() < 0 || req.startMinute() > 1439
                || req.endMinute() < 1 || req.endMinute() > 1440
                || req.startMinute() == req.endMinute()) {
            throw new BadRequestException("tariff.window.invalid");
        }
        if (req.multiplierPct() < 1 || req.multiplierPct() > 10_000) {
            throw new BadRequestException("tariff.multiplier.invalid");
        }
    }

    static TariffWindowResponse toDto(TariffWindowEntity w) {
        return new TariffWindowResponse(w.getId(), w.getVersion(), w.getDayOfWeek(),
                w.getStartMinute(), w.getEndMinute(), w.getMultiplierPct(), w.getLabel());
    }
}
