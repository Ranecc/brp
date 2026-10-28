package dev.brp.service;

import dev.brp.dto.MonetaryDtos.SnapshotResponse;

/**
 * The shared weekly snapshot (docs/12 §3): one number feeding Gini,
 * velocity, the levies and APS — never recomputed per consumer.
 */
public interface SnapshotService {

    SnapshotResponse capture();

    SnapshotResponse latest();
}
