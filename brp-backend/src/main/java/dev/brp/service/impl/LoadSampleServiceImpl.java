package dev.brp.service.impl;

import dev.brp.dto.LoadDtos.ForecastHour;
import dev.brp.dto.LoadDtos.ForecastResponse;
import dev.brp.dto.LoadDtos.SampleResponse;
import dev.brp.entity.LoadProfileEntity;
import dev.brp.entity.LoadSampleEntity;
import dev.brp.entity.ProfileRunEntity;
import dev.brp.repository.LoadProfileRepository;
import dev.brp.repository.LoadSampleRepository;
import dev.brp.repository.ProfileRunRepository;
import dev.brp.service.LoadSampleService;
import dev.brp.service.TariffService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LoadSampleServiceImpl implements LoadSampleService {

    /** Below this many contributing days the forecaster admits it is guessing. */
    static final int COLD_START_DAYS = 3;
    private static final double TICK_BUDGET_MS = 50.0;

    private final LoadSampleRepository samples;
    private final LoadProfileRepository profile;
    private final ProfileRunRepository profileRuns;
    private final TariffService tariff;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ZoneId zone;
    private final double profileAlpha;
    private final double liveAlpha;
    private final double liveDamp;
    private final int retentionDays;
    private final int drBudgetMspt;

    public LoadSampleServiceImpl(LoadSampleRepository samples,
                                 LoadProfileRepository profile,
                                 ProfileRunRepository profileRuns,
                                 TariffService tariff,
                                 TransactionTemplate tx,
                                 Clock clock,
                                 @Value("${brp.reports.zone:UTC}") String zoneId,
                                 @Value("${brp.load.profile-alpha:0.05}") double profileAlpha,
                                 @Value("${brp.load.live-alpha:0.3}") double liveAlpha,
                                 @Value("${brp.load.live-damp:0.5}") double liveDamp,
                                 @Value("${brp.load.retention-days:90}") int retentionDays,
                                 @Value("${brp.dr.mspt-budget:40}") int drBudgetMspt) {
        this.samples = samples;
        this.profile = profile;
        this.profileRuns = profileRuns;
        this.tariff = tariff;
        this.tx = tx;
        this.clock = clock;
        this.zone = ZoneId.of(zoneId);
        this.profileAlpha = profileAlpha;
        this.liveAlpha = liveAlpha;
        this.liveDamp = liveDamp;
        this.retentionDays = retentionDays;
        this.drBudgetMspt = drBudgetMspt;
    }

    @Override
    public void record(double mspt, int players, int entities, int chunks, int forced) {
        LocalDateTime minute = LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        tx.executeWithoutResult(status -> {
            LoadSampleEntity row = samples.findForUpdate(minute);
            if (row == null) {
                samples.save(new LoadSampleEntity(minute, mspt, players, entities, chunks, forced));
            } else {
                row.addSample(mspt, players, entities, chunks, forced);
            }
        });
    }

    @Override
    public List<SampleResponse> samples(int hours) {
        LocalDateTime since = LocalDateTime.now(clock).minus(Duration.ofHours(hours));
        return samples.findByBucketMinuteGreaterThanEqualOrderByBucketMinuteAsc(since).stream()
                .map(s -> new SampleResponse(s.getBucketMinute().toString(), s.getSampleCount(),
                        s.getMsptAvg(), s.getMsptMax(), s.getPlayerMax(), s.getEntityMax(),
                        s.getChunkMax(), s.getForcedMax()))
                .toList();
    }

    /**
     * Daily refresh (docs/13 §2): blend each observed minute into its
     * minute-of-day EWMA, then prune past retention. The insert-only run
     * guard makes concurrent/duplicate triggers idempotent. Deliberately NOT
     * one big @Transactional: a duplicate guard insert must not mark the
     * whole thing rollback-only — the guard commits alone, the blend gets
     * its own transaction.
     */
    @Override
    @Scheduled(cron = "${brp.load.profile-cron:0 20 0 * * *}", zone = "${brp.reports.zone:UTC}")
    public boolean refreshProfile() {
        LocalDate today = LocalDate.now(clock);
        try {
            profileRuns.saveAndFlush(new ProfileRunEntity(today));
        } catch (DataIntegrityViolationException alreadyRan) {
            return false;
        }
        LocalDateTime dayStart = today.atStartOfDay();
        List<LoadSampleEntity> todays = samples.findByBucketMinuteBetweenOrderByBucketMinuteAsc(
                dayStart, dayStart.plusDays(1));
        tx.executeWithoutResult(status -> {
            for (LoadSampleEntity s : todays) {
                int minuteOfDay = s.getBucketMinute().getHour() * 60 + s.getBucketMinute().getMinute();
                LoadProfileEntity p = profile.findById(minuteOfDay).orElse(null);
                if (p == null) {
                    profile.save(new LoadProfileEntity(minuteOfDay, s.getMsptAvg()));
                } else {
                    p.blend(s.getMsptAvg(), profileAlpha);
                }
            }
        });
        samples.deleteByBucketMinuteBefore(dayStart.minusDays(retentionDays));
        return true;
    }

    @Override
    public ForecastResponse forecast(int hours) {
        Map<Integer, Double> profileByMinute = new HashMap<>();
        int maxDays = 0;
        for (LoadProfileEntity p : profile.findAll()) {
            profileByMinute.put(p.getMinuteOfDay(), p.getEwmaMspt());
            maxDays = Math.max(maxDays, p.getSampleDays());
        }
        boolean coldStart = maxDays < COLD_START_DAYS;

        LocalDateTime now = LocalDateTime.now(clock);
        double live = liveMspt();

        int nowMinute = now.getHour() * 60 + now.getMinute();
        double profileNow = profileByMinute.getOrDefault(nowMinute, live);
        double anchor = coldStart ? live : profileNow;

        List<ForecastHour> out = new ArrayList<>();
        LocalDateTime hourStart = now.truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        for (int h = 0; h < hours; h++) {
            LocalDateTime start = hourStart.plusHours(h);
            double predicted = predictedForHour(start, profileByMinute, live, anchor, coldStart);
            var res = tariff.resolveAt(start.atZone(zone).toInstant());
            boolean schedulable = predicted <= drBudgetMspt;
            out.add(new ForecastHour(start.toString(), round(predicted),
                    round(LoadForecastMath.utilization(predicted)),
                    res.label(), res.multiplierPct(), schedulable));
        }
        return new ForecastResponse(coldStart, maxDays, round(live), out);
    }

    @Override
    public double liveMspt() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Double> recent = samples
                .findByBucketMinuteGreaterThanEqualOrderByBucketMinuteAsc(now.minusMinutes(30))
                .stream().map(LoadSampleEntity::getMsptAvg).toList();
        return LoadForecastMath.ewma(recent, liveAlpha);
    }

    @Override
    public double avgMsptBetween(LocalDateTime from, LocalDateTime to) {
        List<LoadSampleEntity> rows = samples.findByBucketMinuteBetweenOrderByBucketMinuteAsc(from, to);
        long weight = 0;
        double weighted = 0;
        for (LoadSampleEntity row : rows) {
            weighted += row.getMsptAvg() * row.getSampleCount();
            weight += row.getSampleCount();
        }
        return weight == 0 ? 0.0 : weighted / weight;
    }

    /** Mean of the per-minute prediction across the hour. */
    private double predictedForHour(LocalDateTime hourStart, Map<Integer, Double> profileByMinute,
                                    double live, double anchor, boolean coldStart) {
        if (coldStart) {
            return live;
        }
        double sum = 0;
        for (int m = 0; m < 60; m++) {
            int minuteOfDay = hourStart.getHour() * 60 + m;
            double profileMinute = profileByMinute.getOrDefault(minuteOfDay, anchor);
            sum += LoadForecastMath.predict(profileMinute, live, anchor, liveDamp);
        }
        return sum / 60.0;
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
