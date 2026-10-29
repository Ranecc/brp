package dev.brp.service;

import dev.brp.dto.LoadDtos.CreditStatusResponse;
import dev.brp.entity.CreditEventEntity;

import java.util.List;

/**
 * Credit & debt (docs/13 §6): BRC-denominated overdraft with a price (daily
 * simple interest), a limit with teeth (RESTRICTED closes the collateral —
 * the chunks), and a board-only exit from DEFAULT. Movement and play stay
 * free; what stops is high load.
 */
public interface CreditService {

    CreditStatusResponse status(String tenantId);

    /** Admin sets (and pins) the limit; auto-recompute stands down. */
    CreditStatusResponse setLimit(String tenantId, long limitCents);

    /** Pay debt from the treasury wallet. */
    CreditStatusResponse repay(String tenantId, long amountCents);

    /** Admin-only exit from DEFAULTED (lands one step below, at WATCH). */
    CreditStatusResponse reinstate(String tenantId);

    /** True when the credit desk blocks new high-load commitments. */
    boolean blocked(String tenantId);

    List<CreditEventEntity> events(String tenantId);

    /**
     * The daily desk: sweep overdrafts into drawn, accrue interest, walk the
     * status machine, default the grace-expired. Idempotent per date.
     * @return true when this call ran the desk
     */
    boolean runDailyDesk();

    /** Weekly auto-limit recompute for unpinned lines. */
    void recomputeLimits();
}
