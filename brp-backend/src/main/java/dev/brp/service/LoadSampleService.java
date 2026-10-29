package dev.brp.service;

import dev.brp.dto.LoadDtos.ForecastResponse;
import dev.brp.dto.LoadDtos.SampleResponse;

import java.util.List;

/**
 * The load plane (docs/13 §2): minute-bucketed MSPT samples with player,
 * entity and chunk census; the durable minute-of-day profile; the hourly
 * forecast that demand response and the TPS bonds both settle against.
 */
public interface LoadSampleService {

    /** Upsert the current minute with one heartbeat sample (row-locked). */
    void record(double mspt, int players, int entities, int chunks, int forced);

    List<SampleResponse> samples(int hours);

    /**
     * Daily profile refresh: EWMA-blend yesterday's minutes into the
     * minute-of-day profile, prune past retention. Idempotent per date.
     * @return true when this call actually ran the refresh
     */
    boolean refreshProfile();

    ForecastResponse forecast(int hours);

    /** The 30-minute EWMA of mspt — the "live" the shed check reads. */
    double liveMspt();
}
