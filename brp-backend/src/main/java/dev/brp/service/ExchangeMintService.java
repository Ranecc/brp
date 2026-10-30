package dev.brp.service;

import dev.brp.dto.ExchangeDtos.MintStateResponse;
import dev.brp.entity.ExchangeItemEntity;
import dev.brp.service.impl.AuctionMath.Quote;

/**
 * The listed mint (docs/14 §4): standing two-sided quotes around the
 * reference, capped by depth, by warehouse stock, and by what it can
 * actually pay. A mint that buys without paying is a printer with a coat
 * of paint.
 */
public interface ExchangeMintService {

    /** @return the mint's bid quote, or null when the reserve says no. */
    Quote bidQuote(ExchangeItemEntity item);

    /** @return the mint's ask quote, or null when the warehouse is bare. */
    Quote askQuote(ExchangeItemEntity item);

    MintStateResponse state();

    /** Creates the mint's treasury and sink on first touch — idempotent. */
    void ensureAccounts();

    /** Daily: the spread the mint captured goes to SYSTEM_SINK, less the float. */
    void sweepSeigniorage();
}
