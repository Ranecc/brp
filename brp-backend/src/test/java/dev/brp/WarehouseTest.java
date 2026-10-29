package dev.brp;

import dev.brp.common.BadRequestException;
import dev.brp.dto.ExchangeDtos.CatalogEntryRequest;
import dev.brp.dto.ExchangeDtos.ClaimResponse;
import dev.brp.dto.ExchangeDtos.WarehouseRowResponse;
import dev.brp.repository.ExchangeItemRepository;
import dev.brp.repository.ExchangeMovementRepository;
import dev.brp.repository.WarehouseRepository;
import dev.brp.service.ExchangeCatalogService;
import dev.brp.service.ExchangeWarehouseService;
import dev.brp.entity.WarehouseRowEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Custody (docs/14 §8): goods move through one append-only movement log;
 * replays never inflate a shelf, holds answer to ASKs, and the reconciliation
 * identity (warehouse ≡ Σ movements) survives every sequence thrown at it.
 */
@SpringBootTest(properties = "brp.exchange.preset=free")
class WarehouseTest {

    @Autowired ExchangeWarehouseService warehouse;
    @Autowired ExchangeCatalogService catalog;
    @Autowired ExchangeItemRepository items;
    @Autowired ExchangeMovementRepository movements;
    @Autowired WarehouseRepository warehouseRepo;

    private String tenant;

    @AfterEach
    void sweep() {
        warehouseRepo.deleteAll();
        movements.deleteAll();
        items.deleteAllById(List.of("testmod:warehouse_a", "testmod:warehouse_b"));
        tenant = null;
    }

    private String item() {
        String id = "testmod:warehouse_a";
        if (!items.existsById(id)) {
            catalog.pushCatalog(List.of(new CatalogEntryRequest(id, null)));
        }
        return id;
    }

    private WarehouseRowResponse deposit(long qty) {
        if (tenant == null) {
            tenant = "wh-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return warehouse.deposit(tenant, item(), qty, UUID.randomUUID().toString());
    }

    private long[] sum(String t, String id) {
        return movements.findAll().stream()
                .filter(m -> m.getTenantId().equals(t) && m.getItemId().equals(id))
                .collect(() -> new long[2], (a, m) -> {
                    a[0] += m.getDeltaFree();
                    a[1] += m.getDeltaHeld();
                }, (a, b) -> {});
    }

    private void assertBooksAgree(String t, String id) {
        WarehouseRowEntity shelf = warehouseRepo.findById(new WarehouseRowEntity.Pk(t, id)).orElseThrow();
        long[] sums = sum(t, id);
        assertThat(shelf.getQtyFree()).as("free ≡ Σ movements").isEqualTo(sums[0]);
        assertThat(shelf.getQtyHeld()).as("held ≡ Σ movements").isEqualTo(sums[1]);
    }

    @Test
    void depositWithdrawLifecycleKeepsTheBooksAgreeing() {
        WarehouseRowResponse shelf = deposit(10);
        assertThat(shelf.qtyFree()).isEqualTo(10);
        assertThat(shelf.qtyHeld()).isZero();

        ClaimResponse claim = warehouse.withdraw(tenant, item(), 4, "w1");
        assertThat(claim.status()).isEqualTo("PENDING");
        assertThat(warehouse.holdings(tenant).get(0).qtyFree()).isEqualTo(6);

        warehouse.confirmWithdraw(tenant, claim.id());
        assertThat(warehouse.claims(tenant).get(0).status()).isEqualTo("CONFIRMED");
        assertBooksAgree(tenant, item());

        ClaimResponse second = warehouse.withdraw(tenant, item(), 2, "w2");
        warehouse.releaseWithdraw(tenant, second.id());
        assertThat(warehouse.holdings(tenant).get(0).qtyFree()).isEqualTo(6);  // 10 − 4 − 2 + 2
        assertThat(warehouse.claims(tenant).stream()
                .filter(c -> c.id().equals(second.id())).findFirst().orElseThrow().status())
                .isEqualTo("RELEASED");
        assertBooksAgree(tenant, item());
    }

    @Test
    void depositReplayNeverInflatesTheShelf() {
        String id = item();
        String tenantId = "wh-" + UUID.randomUUID().toString().substring(0, 8);
        tenant = tenantId;
        String idem = UUID.randomUUID().toString();
        warehouse.deposit(tenantId, id, 7, idem);
        warehouse.deposit(tenantId, id, 7, idem);
        warehouse.deposit(tenantId, id, 7, idem);
        assertThat(warehouse.holdings(tenantId).get(0).qtyFree()).isEqualTo(7);
        assertBooksAgree(tenantId, id);
    }

    @Test
    void withdrawReplayReturnsTheSameClaim() {
        deposit(5);
        String idem = "w-" + UUID.randomUUID();
        ClaimResponse first = warehouse.withdraw(tenant, item(), 2, idem);
        ClaimResponse again = warehouse.withdraw(tenant, item(), 2, idem);
        assertThat(again.id()).isEqualTo(first.id());
        assertThat(warehouse.holdings(tenant).get(0).qtyFree()).isEqualTo(3);
    }

    @Test
    void overdraftsAreRefusedAtTheDoor() {
        deposit(3);
        assertThatThrownBy(() -> warehouse.withdraw(tenant, item(), 4, "over"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> warehouse.deposit(tenant, item(), 0, "zero"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> warehouse.deposit(tenant, "testmod:never-pushed", 1, "ghost"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void holdsAnswerToAsksAndReleaseUndoesThem() {
        deposit(5);
        warehouse.hold(tenant, item(), 3, "order-1", "hold:1");
        WarehouseRowResponse held = warehouse.holdings(tenant).get(0);
        assertThat(held.qtyFree()).isEqualTo(2);
        assertThat(held.qtyHeld()).isEqualTo(3);

        assertThatThrownBy(() -> warehouse.hold(tenant, item(), 3, "order-2", "hold:2"))
                .isInstanceOf(BadRequestException.class);   // only 2 free

        warehouse.release(tenant, item(), 3, "order-1", "release:1");
        assertThat(warehouse.holdings(tenant).get(0).qtyFree()).isEqualTo(5);
        assertThat(warehouse.holdings(tenant).get(0).qtyHeld()).isZero();
        assertBooksAgree(tenant, item());
    }

    @Test
    void deliveryMovesCustodyBetweenTenants() {
        deposit(6);
        warehouse.hold(tenant, item(), 6, "order-1", "hold:1");

        String buyer = "wh-buyer-" + UUID.randomUUID().toString().substring(0, 6);
        warehouse.deliver(tenant, buyer, item(), 4, "fill-1", "fill:1");
        assertThat(warehouse.holdings(tenant).get(0).qtyHeld()).isEqualTo(2);
        assertThat(warehouse.holdings(buyer).get(0).qtyFree()).isEqualTo(4);
        assertBooksAgree(tenant, item());
        assertBooksAgree(buyer, item());
    }

    @Test
    void claimBoundariesBelongToTheirOwner() {
        deposit(4);
        ClaimResponse claim = warehouse.withdraw(tenant, item(), 4, "w1");
        String stranger = "wh-stranger-" + UUID.randomUUID().toString().substring(0, 6);
        assertThatThrownBy(() -> warehouse.confirmWithdraw(stranger, claim.id()))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> warehouse.confirmWithdraw(tenant, 999999L))
                .isInstanceOf(BadRequestException.class);
    }
}
