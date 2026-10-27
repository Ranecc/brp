package dev.brp.service;

import dev.brp.dto.ReconciliationDtos.FindingResponse;
import dev.brp.dto.ReconciliationDtos.RunReport;

import java.util.List;

/**
 * The reconciliation desk (docs/05 §3, docs/10 R-1). v1 proves the monetary
 * invariant — Σ account balances ≡ 0 per currency — and files a finding for
 * every currency that disagrees. The apology template writes itself.
 */
public interface ReconciliationService {

    /** Run the desk: one pass over all currencies. Scheduled hourly. */
    RunReport reconcile();

    List<FindingResponse> findings();
}
