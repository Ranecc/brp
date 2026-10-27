package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Owner-configured currency; the ledger refuses unregistered codes. */
@Entity
@Table(name = "currencies")
public class CurrencyEntity {

    @Id
    private String code;

    private String name;
    private String symbol;
    private int decimals;
    private String status = "ACTIVE";
    /** NULL = pure ledger currency; the mint window dispenses non-null items (docs/03 section 9). */
    private String bearerItemId;
    private Instant createdAt = Instant.now();

    protected CurrencyEntity() {}

    public CurrencyEntity(String code, String name, String symbol, int decimals) {
        this.code = code;
        this.name = name;
        this.symbol = symbol;
        this.decimals = decimals;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getSymbol() { return symbol; }
    public int getDecimals() { return decimals; }
    public String getStatus() { return status; }
    public String getBearerItemId() { return bearerItemId; }

    public void setName(String name) { this.name = name; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setBearerItemId(String bearerItemId) { this.bearerItemId = bearerItemId; }
}
