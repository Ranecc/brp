package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** One observed market price (docs/12 §12): CPI's food chain starts here. */
@Entity
@Table(name = "price_observations")
public class PriceObservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String itemId;
    private long priceCents;
    private String currency = "BRC";
    private long qty;
    private String venue;
    private String source = "manual";
    private Instant observedAt = Instant.now();

    protected PriceObservationEntity() {}

    public PriceObservationEntity(String itemId, long priceCents, String currency,
                                  long qty, String venue, String source) {
        this.itemId = itemId;
        this.priceCents = priceCents;
        this.currency = currency;
        this.qty = qty;
        this.venue = venue;
        this.source = source;
    }

    public Long getId() { return id; }
    public String getItemId() { return itemId; }
    public long getPriceCents() { return priceCents; }
    public String getCurrency() { return currency; }
    public long getQty() { return qty; }
    public String getVenue() { return venue; }
    public String getSource() { return source; }
    public Instant getObservedAt() { return observedAt; }
}
