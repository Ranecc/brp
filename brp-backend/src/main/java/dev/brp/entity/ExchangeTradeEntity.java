package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * One bilateral fill at the uniform clearing price (docs/14 §6). Mint legs
 * carry the mint tenant on buyer/seller and null order ids — the state's
 * book is the same shape as everyone else's.
 */
@Entity
@Table(name = "exchange_trades")
public class ExchangeTradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "batch_seq")
    private long batchSeq;
    @Column(name = "item_id")
    private String itemId;
    private String buyer;
    private String seller;
    private long qty;
    @Column(name = "price_cents")
    private long priceCents;
    @Column(name = "fee_cents")
    private long feeCents;
    @Column(name = "bid_order_id")
    private Long bidOrderId;
    @Column(name = "ask_order_id")
    private Long askOrderId;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected ExchangeTradeEntity() {}

    public ExchangeTradeEntity(long batchSeq, String itemId, String buyer,
                               String seller, long qty, long priceCents,
                               long feeCents, Long bidOrderId, Long askOrderId) {
        this.batchSeq = batchSeq;
        this.itemId = itemId;
        this.buyer = buyer;
        this.seller = seller;
        this.qty = qty;
        this.priceCents = priceCents;
        this.feeCents = feeCents;
        this.bidOrderId = bidOrderId;
        this.askOrderId = askOrderId;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public long getBatchSeq() { return batchSeq; }
    public String getItemId() { return itemId; }
    public String getBuyer() { return buyer; }
    public String getSeller() { return seller; }
    public long getQty() { return qty; }
    public long getPriceCents() { return priceCents; }
    public long getFeeCents() { return feeCents; }
    public Long getBidOrderId() { return bidOrderId; }
    public Long getAskOrderId() { return askOrderId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
