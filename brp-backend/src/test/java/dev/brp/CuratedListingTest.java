package dev.brp;

import dev.brp.dto.ExchangeDtos.CatalogEntryRequest;
import dev.brp.dto.ExchangeDtos.ListingRequest;
import dev.brp.repository.ExchangeItemRepository;
import dev.brp.service.ExchangeCatalogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CURATED_CATALOG (docs/14 §5): nothing moves without a row — the lever
 * that keeps villager exclusives villager-owned, if the owner wants that.
 */
@SpringBootTest(properties = "brp.exchange.preset=curated")
class CuratedListingTest {

    @Autowired ExchangeCatalogService catalog;
    @Autowired ExchangeItemRepository items;

    private final List<String> mine = new ArrayList<>();

    @AfterEach
    void sweep() {
        items.deleteAllById(mine);
        mine.clear();
    }

    @Test
    void nothingIsTradableUntilTheOwnerListsIt() {
        String id = "testmod:curated_a";
        mine.add(id);
        catalog.pushCatalog(List.of(new CatalogEntryRequest(id, null)));
        assertThat(catalog.tradable(id)).isFalse();     // DEFAULT is inert here
        catalog.list(id, new ListingRequest("LISTED", 250L, null, null));
        assertThat(catalog.tradable(id)).isTrue();
        catalog.list(id, new ListingRequest("DEFAULT", null, null, null));
        assertThat(catalog.tradable(id)).isFalse();     // delisted: the preset reclaims it
    }
}
