package dev.brp.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "accounts")
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String tenantId;
    private String accountNo;
    private String accountType;
    private String currency;
    private long balanceCents;

    @Version
    private long version;

    protected AccountEntity() {}

    public AccountEntity(String tenantId, String accountNo, String accountType, String currency) {
        this.tenantId = tenantId;
        this.accountNo = accountNo;
        this.accountType = accountType;
        this.currency = currency;
    }

    public Long getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getAccountNo() { return accountNo; }
    public String getAccountType() { return accountType; }
    public String getCurrency() { return currency; }
    public long getBalanceCents() { return balanceCents; }

    public void apply(long signedDeltaCents) {
        // exact arithmetic: overflow surfaces as a 400 at the service boundary,
        // never a silent wrap (money paths are no-joke zones)
        this.balanceCents = Math.addExact(this.balanceCents, signedDeltaCents);
    }
}
