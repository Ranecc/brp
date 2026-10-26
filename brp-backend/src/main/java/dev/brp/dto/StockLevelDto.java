package dev.brp.dto;

import java.time.Instant;

/** WMS stock level response DTO (stale by design; capturedAt is the label). */
public record StockLevelDto(String containerId, String itemId, long amount, Instant capturedAt) {}
