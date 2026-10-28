package dev.brp.service;

import dev.brp.dto.MonetaryDtos.RegimeResponse;

/**
 * The antitrust regime with hysteresis (docs/12 §4): >= 0.8 arms, < 0.7
 * disarms, between holds. Flapping taxes are player-facing twitching.
 */
public interface RegimeService {

    /** Feed one snapshot's readings; returns the state after processing. */
    String afterSnapshot(double gini, double topShare);

    boolean active();

    RegimeResponse regime();
}
