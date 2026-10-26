package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Generic idempotency store backing @Idempotent (V2__players_saga_idem.sql). */
@Entity
@Table(name = "idem_store")
public class IdemStoreRow {

    @Id
    private String idemKey;

    private String responseJson;
    private Instant createdAt = Instant.now();

    protected IdemStoreRow() {}

    public IdemStoreRow(String idemKey, String responseJson) {
        this.idemKey = idemKey;
        this.responseJson = responseJson;
    }

    public String getIdemKey() { return idemKey; }
    public String getResponseJson() { return responseJson; }
    public Instant getCreatedAt() { return createdAt; }
}
