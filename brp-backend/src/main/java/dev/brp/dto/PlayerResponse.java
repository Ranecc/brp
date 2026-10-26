package dev.brp.dto;

import java.time.Instant;

/** Player API response DTO (entity never leaves the service layer). */
public record PlayerResponse(String uuid, String name, Instant firstSeen, Instant lastSeen) {}
