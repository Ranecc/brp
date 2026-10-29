package dev.brp.service;

import java.util.Map;

/**
 * Gresham by payment rail (docs/12 §10): 7-day sliding window over
 * rail:-memoed journals; single-leg share > 95% is a first-class crisis
 * signal. The prescription stays human.
 */
public interface GreshamService {

    /** @return leg -> share (0..1); scan files BRP-0903 when a leg > 95%. */
    Map<String, Double> scan();
}
