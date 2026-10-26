package dev.brp.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Append-only journal. In production, additionally revoke UPDATE/DELETE at the
 * database role level (see V1__init.sql).
 */
@Entity
@Table(name = "journal_entries")
public class JournalEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String journalId;
    private String idemKey;
    private String tenantId;
    private Long accountId;
    /** DEBIT | CREDIT */
    private String direction;
    private long amountCents;
    private String currency;
    private String memo;
    private Instant createdAt = Instant.now();

    protected JournalEntryEntity() {}

    public JournalEntryEntity(String journalId, String idemKey, String tenantId,
                              Long accountId, String direction, long amountCents,
                              String currency, String memo) {
        this.journalId = journalId;
        this.idemKey = idemKey;
        this.tenantId = tenantId;
        this.accountId = accountId;
        this.direction = direction;
        this.amountCents = amountCents;
        this.currency = currency;
        this.memo = memo;
    }

    public Long getId() { return id; }
    public String getJournalId() { return journalId; }
    public String getIdemKey() { return idemKey; }
    public long getAmountCents() { return amountCents; }
    public String getDirection() { return direction; }
    public Instant getCreatedAt() { return createdAt; }
}
