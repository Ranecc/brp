package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * A tradable identity (docs/14 §3): the namespaced registry id IS the
 * instrument. Listing state is governance-owned — the catalog push writes
 * display names and nothing else.
 */
@Entity
@Table(name = "exchange_items")
public class ExchangeItemEntity {

    public static final String DEFAULT = "DEFAULT";
    public static final String LISTED = "LISTED";
    public static final String BLOCKED = "BLOCKED";

    @Id
    @Column(name = "item_id")
    private String itemId;

    @Column(name = "display_name")
    private String displayName;
    @Column(name = "listed_state")
    private String listedState;
    @Column(name = "ref_cents")
    private long refCents;
    private boolean pinned;
    @Column(name = "created_at")
    private OffsetDateTime createdAt;
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected ExchangeItemEntity() {}

    public ExchangeItemEntity(String itemId, String displayName, long refCents) {
        this.itemId = itemId;
        this.displayName = displayName;
        this.listedState = DEFAULT;
        this.refCents = refCents;
        this.pinned = false;
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public String getItemId() { return itemId; }
    public String getDisplayName() { return displayName; }
    public String getListedState() { return listedState; }
    public long getRefCents() { return refCents; }
    public boolean isPinned() { return pinned; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public void rename(String displayName) { this.displayName = displayName; }

    public void setListedState(String listedState) { this.listedState = listedState; }
    public void setRefCents(long refCents) { this.refCents = refCents; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }

    public void touch() { this.updatedAt = OffsetDateTime.now(); }

    /** The mint's two quotes around the reference (docs/14 §4). */
    public long mintBidCents(long fullSpreadBips) {
        return refCents - refCents * fullSpreadBips / 20000;
    }

    public long mintAskCents(long fullSpreadBips) {
        return refCents + refCents * fullSpreadBips / 20000;
    }

    /** The crawl (docs/14 §4): EWMA toward the clearing price. */
    public void crawl(double clearCents, double factor) {
        this.refCents = Math.round(refCents + factor * (clearCents - refCents));
        touch();
    }
}
