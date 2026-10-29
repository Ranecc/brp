package dev.brp.service;

import dev.brp.saga.SagaEngine;

/**
 * The mint window (docs/12 §8): FAUCET journal FIRST, dispense SECOND;
 * compensations run reverse. The vault may not disagree with the ledger.
 */
public interface MintService {

    SagaEngine.Outcome mint(String tenantId, String containerRef, String itemId,
                            long qty, long cents, String correlation);

    void registerVault(String tenantId, String containerRef, String itemId);
}
