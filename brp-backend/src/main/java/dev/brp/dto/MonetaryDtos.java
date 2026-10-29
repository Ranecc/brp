package dev.brp.dto;

import java.util.List;

/** P3 monetary DTOs (docs/12). */
public final class MonetaryDtos {

    private MonetaryDtos() {}

    public record RegimeResponse(String name, String state, double lastReading, String since) {}

    public record SnapshotTenantRow(String tenantId, long wealthCents, double velocity) {}

    public record SnapshotResponse(String weekOf, double gini, double topShare,
                                    String regimeAfter, List<SnapshotTenantRow> tenants) {}

    /** Wash finding, in its own honest shape (docs/12 §11). */
    public record WashFindingResponse(long id, String tenantId, String accountsCsv,
                                      int cycleLen, double score, String createdAt) {}

    public record WealthAssessmentRow(String weekOf, String tenantId, long bracketCents,
                                      boolean surcharge, long amountCents, boolean posted) {}
}
