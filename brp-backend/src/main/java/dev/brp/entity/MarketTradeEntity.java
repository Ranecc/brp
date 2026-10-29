package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/** One execution: settled BRC + delivered quota + the treasury's fee, one row. */
@Entity
@Table(name = "market_trades")
public class MarketTradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idem_key")
    private String idemKey;
    @Column(name = "ask_id")
    private Long askId;
    @Column(name = "bid_id")
    private Long bidId;
    private long qty;
    @Column(name = "price_cents")
    private long priceCents;
    @Column(name = "fee_cents")
    private long feeCents;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    protected MarketTradeEntity() {}

    public MarketTradeEntity(String idemKey, Long askId, Long bidId,
                             long qty, long priceCents, long feeCents) {
        this.idemKey = idemKey;
        this.askId = askId;
        this.bidId = bidId;
        this.qty = qty;
        this.priceCents = priceCents;
        this.feeCents = feeCents;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }
    public String getIdemKey() { return idemKey; }
    public Long getAskId() { return askId; }
    public Long getBidId() { return bidId; }
    public long getQty() { return qty; }
    public long getPriceCents() { return priceCents; }
    public long getFeeCents() { return feeCents; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
