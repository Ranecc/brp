package dev.brp.service;

import dev.brp.dto.LoadDtos.AddTariffWindowRequest;
import dev.brp.dto.LoadDtos.TariffWindowResponse;

import java.time.Instant;
import java.util.List;

/**
 * Time-of-use tariff (docs/13 §4): versioned windows, resolution = highest
 * version present, first matching row, default 100%. Version 0 is owned by
 * the legacy brp.chunk.billing.peak-* config keys and re-derived at boot;
 * versions 1+ are API-owned.
 */
public interface TariffService {

    /** Multiplier percent at an instant, in the billing zone. Never null. */
    int multiplierPctAt(Instant when);

    /** Resolution with the window label (VALLEY/SHOULDER/PEAK or SHOULDER default). */
    Resolution resolveAt(Instant when);

    record Resolution(int multiplierPct, String label, int version) {}

    List<TariffWindowResponse> windows();

    /** Start the next version: copy latest rows, append the new window. */
    TariffWindowResponse addWindow(AddTariffWindowRequest req);
}
