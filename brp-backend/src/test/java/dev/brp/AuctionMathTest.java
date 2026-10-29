package dev.brp;

import dev.brp.service.impl.AuctionMath;
import dev.brp.service.impl.AuctionMath.Fill;
import dev.brp.service.impl.AuctionMath.Quote;
import dev.brp.service.impl.AuctionMath.Result;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The kink table (docs/14 §6): max volume, then flat book, then the anchor.
 * The 101/1000 case is the reason the anchor exists — a thin book prints
 * the posted price, never the midpoint of a standoff.
 */
class AuctionMathTest {

    private Quote bid(long seq, String tenant, long qty, long price) {
        return new Quote(seq, tenant, seq, qty, price);
    }

    private Quote ask(long seq, String tenant, long qty, long price) {
        return new Quote(seq, tenant, 1000 + seq, qty, price);
    }

    @Test
    void theKinkMaximizesVolumeThenPicksTheFlatterBook() {
        // vol ties at 102 and 105 (10 each); surplus ties; 102 sits near the anchor
        Result r = AuctionMath.clear(
                List.of(bid(1, "p1", 5, 100), bid(2, "p2", 10, 105)),
                List.of(ask(3, "p3", 8, 95), ask(4, "p4", 12, 102)),
                103);
        assertThat(r.clearPrice()).isEqualTo(102);
        assertThat(r.clearedQty()).isEqualTo(10);
    }

    @Test
    void imbalanceBreaksVolumeTies() {
        // vol ties at 90 and 95; the book is flat only at 95 — the anchor loses
        Result r = AuctionMath.clear(
                List.of(bid(1, "p1", 10, 90), bid(2, "p2", 10, 100)),
                List.of(ask(3, "p3", 10, 95)),
                90);
        assertThat(r.clearPrice()).isEqualTo(95);
        assertThat(r.surplus()).isZero();
    }

    @Test
    void aThinBookPrintsTheAnchorNeverTheMidpoint() {
        // one bid at 1000, one ask at 101, ref 100: every price in between
        // ties on volume and balance — the anchor decides, and it is not 550
        Result r = AuctionMath.clear(
                List.of(bid(1, "p1", 10, 1000)),
                List.of(ask(2, "mint", 50, 101)),
                100);
        assertThat(r.clearPrice()).isEqualTo(101);
        assertThat(r.clearedQty()).isEqualTo(10);
    }

    @Test
    void theMintFillsLikeAnyOtherParticipant() {
        Result r = AuctionMath.clear(
                List.of(bid(1, "p1", 10, 105)),
                List.of(new Quote(99, "mint", null, 64, 101)),
                100);
        assertThat(r.clearPrice()).isEqualTo(101);
        Fill fill = r.fills().get(0);
        assertThat(fill.buyer()).isEqualTo("p1");
        assertThat(fill.seller()).isEqualTo("mint");
        assertThat(fill.bidOrderId()).isNotNull();
        assertThat(fill.askOrderId()).isNull();
    }

    @Test
    void sameTenantPairsSkipWithoutBlockingOthers() {
        // p1 bids and asks; p2's ask fills p1's bid, then p1's ask waits for
        // p2's bid — the skip belongs to the pair, not to the book
        Result r = AuctionMath.clear(
                List.of(bid(1, "p1", 5, 100), bid(2, "p2", 3, 100)),
                List.of(ask(3, "p2", 5, 100), ask(4, "p1", 5, 100)),
                100);
        assertThat(r.clearPrice()).isEqualTo(100);
        assertThat(r.fills()).hasSize(2);
        assertThat(r.fills().get(0).buyer()).isEqualTo("p1");
        assertThat(r.fills().get(0).seller()).isEqualTo("p2");
        assertThat(r.fills().get(1).buyer()).isEqualTo("p2");
        assertThat(r.fills().get(1).seller()).isEqualTo("p1");
        assertThat(r.clearedQty()).isEqualTo(8);
    }

    @Test
    void timePriorityFeedsTheOldestOrderFirst() {
        Result r = AuctionMath.clear(
                List.of(bid(1, "p1", 3, 100), bid(2, "p2", 3, 100)),
                List.of(ask(3, "p3", 4, 100)),
                100);
        assertThat(r.clearedQty()).isEqualTo(4);
        assertThat(r.fills().get(0).buyer()).isEqualTo("p1");
        assertThat(r.fills().get(0).qty()).isEqualTo(3);
        assertThat(r.fills().get(1).buyer()).isEqualTo("p2");
        assertThat(r.fills().get(1).qty()).isEqualTo(1);
    }

    @Test
    void priceImprovementFlowsToThePassiveSide() {
        // the ask rests at 98, the bid arrives at 100: the uniform print is
        // anchored to ref 100 and the seller keeps every penny above 98
        Result r = AuctionMath.clear(
                List.of(bid(1, "p1", 3, 100)),
                List.of(ask(2, "p2", 1, 98)),
                100);
        assertThat(r.clearPrice()).isEqualTo(100);
        assertThat(r.fills().get(0).priceCents()).isEqualTo(100);
    }

    @Test
    void aBookThatCannotCrossPrintsNothing() {
        Result r = AuctionMath.clear(
                List.of(bid(1, "p1", 5, 50)),
                List.of(ask(2, "p2", 9, 200)),
                100);
        assertThat(r.clearPrice()).isNull();
        assertThat(r.clearedQty()).isZero();
        assertThat(r.fills()).isEmpty();
        assertThat(r.bidVol()).isEqualTo(5);
        assertThat(r.askVol()).isEqualTo(9);
    }

    @Test
    void anEmptyBookIsAnHonestNull() {
        Result r = AuctionMath.clear(List.of(), List.of(), 100);
        assertThat(r.clearPrice()).isNull();
        assertThat(r.fills()).isEmpty();
    }
}
