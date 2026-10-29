package dev.brp.service;

import dev.brp.dto.ExchangeDtos.CatalogEntryRequest;
import dev.brp.dto.ExchangeDtos.CatalogEntryResponse;
import dev.brp.dto.ExchangeDtos.ListingRequest;

import java.util.List;

/**
 * The bourse's catalog and listing governance (docs/14 §5): the mod push
 * may rename, only the owner may list. Two presets — FREE_BOURSE and
 * CURATED_CATALOG — and the owner's per-item word overrides both.
 */
public interface ExchangeCatalogService {

    void pushCatalog(List<CatalogEntryRequest> items);

    CatalogEntryResponse list(String itemId, ListingRequest request);

    List<CatalogEntryResponse> catalog();

    /** The resolution rule: BLOCKED never, LISTED always, DEFAULT = preset. */
    boolean tradable(String itemId);

    CatalogEntryResponse entry(String itemId);
}
