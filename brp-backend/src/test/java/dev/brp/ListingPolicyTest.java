package dev.brp;

import dev.brp.common.BadRequestException;
import dev.brp.dto.ExchangeDtos.CatalogEntryRequest;
import dev.brp.dto.ExchangeDtos.CatalogEntryResponse;
import dev.brp.dto.ExchangeDtos.ListingRequest;
import dev.brp.repository.ExchangeItemRepository;
import dev.brp.service.ExchangeCatalogService;
import dev.brp.service.impl.ExchangeCatalogServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Listing governance (docs/14 §5): the mod push renames, only the owner
 * lists, and the resolution rule is three lines — BLOCKED never, LISTED
 * always, DEFAULT follows the preset. Free bourse context.
 */
@SpringBootTest(properties = "brp.exchange.preset=free")
class ListingPolicyTest {

    @Autowired ExchangeCatalogService catalog;
    @Autowired ExchangeItemRepository items;

    private final List<String> mine = new ArrayList<>();

    private String item(String id) {
        mine.add(id);
        return id;
    }

    @AfterEach
    void sweep() {
        items.deleteAllById(mine);
        mine.clear();
    }

    @Test
    void pushRenamesWithoutTouchingGovernance() {
        String id = item("testmod:push_a");
        catalog.pushCatalog(List.of(new CatalogEntryRequest(id, null)));
        assertThat(catalog.entry(id).displayName()).isEqualTo("Push A");
        assertThat(catalog.entry(id).listedState()).isEqualTo("DEFAULT");
        assertThat(catalog.entry(id).refCents()).isZero();

        catalog.list(id, new ListingRequest("BLOCKED", null, null, null));
        catalog.pushCatalog(List.of(new CatalogEntryRequest(id, "Fancy Push")));
        CatalogEntryResponse after = catalog.entry(id);
        assertThat(after.displayName()).isEqualTo("Fancy Push");
        assertThat(after.listedState()).isEqualTo("BLOCKED");   // governance untouched
    }

    @Test
    void pushRejectsMalformedIdsAndOversizedBatches() {
        assertThatThrownBy(() -> catalog.pushCatalog(List.of(new CatalogEntryRequest("no-namespace", null))))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> catalog.pushCatalog(java.util.Collections.nCopies(
                        ExchangeCatalogServiceImpl.PUSH_BATCH_CAP + 1,
                        new CatalogEntryRequest("testmod:bulk", null))))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listingRequiresAPostedPrice() {
        String id = item("testmod:listing_price");
        catalog.pushCatalog(List.of(new CatalogEntryRequest(id, null)));
        assertThatThrownBy(() -> catalog.list(id, new ListingRequest("LISTED", null, null, null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> catalog.list(id, new ListingRequest("LISTED", 0L, null, null)))
                .isInstanceOf(BadRequestException.class);
        catalog.list(id, new ListingRequest("LISTED", 6400L, null, null));
        assertThat(catalog.entry(id).listedState()).isEqualTo("LISTED");
        assertThat(catalog.tradable(id)).isTrue();
    }

    @Test
    void freePresetResolutionMatrix() {
        String def = item("testmod:matrix_default");
        String listed = item("testmod:matrix_listed");
        String blocked = item("testmod:matrix_blocked");
        catalog.pushCatalog(List.of(new CatalogEntryRequest(def, null),
                new CatalogEntryRequest(listed, null),
                new CatalogEntryRequest(blocked, null)));
        catalog.list(listed, new ListingRequest("LISTED", 100L, null, null));
        catalog.list(blocked, new ListingRequest("BLOCKED", null, null, null));
        assertThat(catalog.tradable(def)).isTrue();     // DEFAULT trades in the free bourse
        assertThat(catalog.tradable(listed)).isTrue();  // LISTED always trades
        assertThat(catalog.tradable(blocked)).isFalse();// BLOCKED never trades
        assertThat(catalog.tradable("testmod:never-pushed")).isFalse();
    }

    @Test
    void theSeedRowIsTheEmeraldLeg() {
        // V22's founding listing: priced at the emerald leg, crawling
        CatalogEntryResponse emerald = catalog.entry("minecraft:emerald");
        assertThat(emerald.listedState()).isEqualTo("LISTED");
        assertThat(emerald.refCents()).isEqualTo(6400L);
        assertThat(emerald.pinned()).isFalse();
    }

    @Test
    void unknownItemsAreHonest() {
        assertThatThrownBy(() -> catalog.entry("testmod:absent"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> catalog.list("testmod:absent",
                        new ListingRequest("LISTED", 1L, null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void displayNamesPrettifyRegistryPaths() {
        assertThat(ExchangeCatalogServiceImpl.displayName("ae2:certus_quartz_crystal", null))
                .isEqualTo("Certus Quartz Crystal");
        assertThat(ExchangeCatalogServiceImpl.displayName("minecraft:emerald", "The Meme of Record"))
                .isEqualTo("The Meme of Record");
    }
}
