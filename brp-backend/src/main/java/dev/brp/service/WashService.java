package dev.brp.service;

import dev.brp.dto.MonetaryDtos.WashFindingResponse;

import java.util.List;

/**
 * Wash-cycle detection (docs/12 §11): SCC over the tenant's transfer graph.
 * Cycles are legal commerce; the score carries the accusation.
 */
public interface WashService {

    ScanReport scan();

    ScanReport scan(String tenantId);

    List<WashFindingResponse> findings();

    record ScanReport(int tenantsScanned, int findingsFiled) {}
}
