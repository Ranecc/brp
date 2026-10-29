package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.io.Serializable;

/**
 * The batch's price observation (docs/14 §7): one row per item per batch,
 * written once. The peg band and CPI both read this table; neither gets to
 * rewrite it.
 */
@Entity
@Table(name = "exchange_clears")
@IdClass(ExchangeClearEntity.Pk.class)
public class ExchangeClearEntity implements Persistable<ExchangeClearEntity.Pk> {

    public record Pk(long batchSeq, String itemId) implements Serializable {}

    @Id @Column(name = "batch_seq") private long batchSeq;
    @Id @Column(name = "item_id") private String itemId;

    @Column(name = "price_cents")
    private Long priceCents;
    @Column(name = "cleared_qty")
    private long clearedQty;
    @Column(name = "bid_vol")
    private long bidVol;
    @Column(name = "ask_vol")
    private long askVol;
    private long surplus;
    @Column(name = "mint_bid_qty")
    private long mintBidQty;
    @Column(name = "mint_ask_qty")
    private long mintAskQty;
    @Column(name = "ref_before")
    private Long refBefore;
    @Column(name = "ref_after")
    private Long refAfter;

    protected ExchangeClearEntity() {}

    public ExchangeClearEntity(long batchSeq, String itemId, Long priceCents,
                               long clearedQty, long bidVol, long askVol,
                               long surplus, long mintBidQty, long mintAskQty,
                               Long refBefore, Long refAfter) {
        this.batchSeq = batchSeq;
        this.itemId = itemId;
        this.priceCents = priceCents;
        this.clearedQty = clearedQty;
        this.bidVol = bidVol;
        this.askVol = askVol;
        this.surplus = surplus;
        this.mintBidQty = mintBidQty;
        this.mintAskQty = mintAskQty;
        this.refBefore = refBefore;
        this.refAfter = refAfter;
    }

    public long getBatchSeq() { return batchSeq; }
    public String getItemId() { return itemId; }
    public Long getPriceCents() { return priceCents; }
    public long getClearedQty() { return clearedQty; }
    public long getBidVol() { return bidVol; }
    public long getAskVol() { return askVol; }
    public long getSurplus() { return surplus; }
    public long getMintBidQty() { return mintBidQty; }
    public long getMintAskQty() { return mintAskQty; }
    public Long getRefBefore() { return refBefore; }
    public Long getRefAfter() { return refAfter; }

    @Override public Pk getId() { return new Pk(batchSeq, itemId); }

    @Override public boolean isNew() { return true; }
}
