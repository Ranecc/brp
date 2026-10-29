package dev.brp.service;

import dev.brp.dto.ExchangeDtos.ClaimResponse;
import dev.brp.dto.ExchangeDtos.WarehouseRowResponse;

import java.util.List;

/**
 * The bourse's warehouse (docs/14 §8): custody with an append-only movement
 * log. Public operations (deposit/withdraw lifecycle) open their own
 * transaction; the hold/release/deliver primitives are called inside the
 * clearing transaction and open none — the books move in the caller's hands.
 */
public interface ExchangeWarehouseService {

    WarehouseRowResponse deposit(String tenantId, String itemId, long qty, String idemKey);

    ClaimResponse withdraw(String tenantId, String itemId, long qty, String idemKey);

    void confirmWithdraw(String tenantId, long claimId);

    void releaseWithdraw(String tenantId, long claimId);

    List<WarehouseRowResponse> holdings(String tenantId);

    List<ClaimResponse> claims(String tenantId);

    /** Order collateral, item edition: free → held. Caller's transaction. */
    void hold(String tenantId, String itemId, long qty, String refId, String idemKey);

    /** The unwind of hold. Caller's transaction. */
    void release(String tenantId, String itemId, long qty, String refId, String idemKey);

    /** Fill settlement: seller's held ships to buyer's free. Caller's transaction. */
    void deliver(String sellerTenantId, String buyerTenantId, String itemId,
                 long qty, String refId, String idemKey);
}
