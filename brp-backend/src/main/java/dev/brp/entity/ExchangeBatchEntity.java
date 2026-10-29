package dev.brp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.domain.Persistable;

import java.time.OffsetDateTime;

/**
 * The batch guard (docs/14 §6): insert-only, isNew()=true — a re-run of the
 * same batch_seq collides on the primary key and loses, which is the whole
 * idempotency story. Assigned identity, never generated.
 */
@Entity
@Table(name = "exchange_batches")
public class ExchangeBatchEntity implements Persistable<Long> {

    @Id
    @Column(name = "batch_seq")
    private Long batchSeq;

    @Column(name = "cleared_at")
    private OffsetDateTime clearedAt;
    @Column(name = "items_cleared")
    private int itemsCleared;
    @Column(name = "trades_cleared")
    private int tradesCleared;

    protected ExchangeBatchEntity() {}

    public ExchangeBatchEntity(long batchSeq) {
        this.batchSeq = batchSeq;
        this.clearedAt = OffsetDateTime.now();
    }

    public Long getBatchSeq() { return batchSeq; }
    public OffsetDateTime getClearedAt() { return clearedAt; }
    public int getItemsCleared() { return itemsCleared; }
    public int getTradesCleared() { return tradesCleared; }

    public void tally(int items, int trades) {
        this.itemsCleared = items;
        this.tradesCleared = trades;
    }

    @Override public Long getId() { return batchSeq; }

    @Override public boolean isNew() { return true; }
}
