package dev.brp.dto;

/** Reconciliation desk DTOs (docs/10 R-1). */
public final class ReconciliationDtos {

    private ReconciliationDtos() {}

    public record FindingResponse(
            long id, String currency, long imbalanceCents, String status, String createdAt) {}

    /** One line per currency checked; imbalanceCents 0 is the only passing grade. */
    public record RunReport(int currenciesChecked, int findingsFiled) {}
}
