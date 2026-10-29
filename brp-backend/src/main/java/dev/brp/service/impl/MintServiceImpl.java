package dev.brp.service.impl;

import dev.brp.common.BadRequestException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.entity.VaultMovementEntity;
import dev.brp.entity.VaultRegistrationEntity;
import dev.brp.repository.VaultMovementRepository;
import dev.brp.repository.VaultRegistrationRepository;
import dev.brp.saga.SagaEngine;
import dev.brp.service.LedgerService;
import dev.brp.service.MintService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * The mint window (docs/12 §8): book-faucet FIRST, dispense SECOND —
 * @Compensable ordering exists precisely for "books moved, goods didn't".
 * Compensation writes a REVERSING journal (insert-only discipline) and a
 * REVOKE movement; the vault may not disagree with the ledger, on pain of
 * paperwork (0904 checks the running balance elsewhere).
 */
@Service
public class MintServiceImpl implements MintService {

    private final SagaEngine engine;
    private final LedgerService ledger;
    private final VaultRegistrationRepository registrations;
    private final VaultMovementRepository movements;

    public MintServiceImpl(SagaEngine engine, LedgerService ledger,
                           VaultRegistrationRepository registrations,
                           VaultMovementRepository movements) {
        this.engine = engine;
        this.ledger = ledger;
        this.registrations = registrations;
        this.movements = movements;
    }

    @PostConstruct
    void register() {
        engine.register(SagaEngine.saga("mint-window")
                .step("book-faucet", p -> book(p), "revoke-faucet", p -> revoke(p))
                .step("dispense", p -> dispense(p), "revoke-dispense", p -> revokeDispense(p))
                .build());
    }

    // saga closures take Object payloads; cast once, read with helpers
    private Object book(Object p) {
        var m = (java.util.Map<?, ?>) p;
        ledger.post((String) m.get("tenantId"), new PostJournalRequest(
                "mint:" + m.get("correlation") + ":faucet", "BRC", List.of(
                        new JournalLine("treasury", "DEBIT", (Long) m.get("cents"), null),
                        new JournalLine("faucet", "CREDIT", (Long) m.get("cents"), null))));
        return null;
    }

    private Object revoke(Object p) {
        var m = (java.util.Map<?, ?>) p;
        ledger.post((String) m.get("tenantId"), new PostJournalRequest(
                "mint:" + m.get("correlation") + ":revoke", "BRC", List.of(
                        new JournalLine("faucet", "DEBIT", (Long) m.get("cents"), null),
                        new JournalLine("treasury", "CREDIT", (Long) m.get("cents"), null))));
        return null;
    }

    private Object dispense(Object p) {
        var m = (java.util.Map<?, ?>) p;
        String tenantId = (String) m.get("tenantId");
        String container = (String) m.get("containerRef");
        var reg = registrations.findById(new VaultRegistrationEntity.Pk(tenantId, container))
                .orElseThrow(() -> new BadRequestException("monetary.vault.unregistered"));
        if (!reg.getItemId().equals(m.get("itemId"))) {
            throw new BadRequestException("monetary.vault.item.mismatch");
        }
        movements.save(new VaultMovementEntity(tenantId, container, "OUT",
                (String) m.get("itemId"), (Long) m.get("qty"), (String) m.get("correlation")));
        return null;
    }

    private Object revokeDispense(Object p) {
        var m = (java.util.Map<?, ?>) p;
        movements.save(new VaultMovementEntity((String) m.get("tenantId"),
                (String) m.get("containerRef"), "REVOKE", (String) m.get("itemId"),
                (Long) m.get("qty"), (String) m.get("correlation")));
        return null;
    }

    @Override
    public SagaEngine.Outcome mint(String tenantId, String containerRef, String itemId,
                                   long qty, long cents, String correlation) {
        ensureAccounts(tenantId);
        return engine.run("mint-window", correlation, Map.of(
                "tenantId", tenantId, "containerRef", containerRef,
                "itemId", itemId, "qty", qty, "cents", cents, "correlation", correlation));
    }

    @Override
    public void registerVault(String tenantId, String containerRef, String itemId) {
        registrations.save(new VaultRegistrationEntity(tenantId, containerRef, itemId));
    }

    private void ensureAccounts(String tenantId) {
        for (String[] spec : new String[][]{{"treasury", "TEAM"}, {"faucet", "SYSTEM_FAUCET"}}) {
            try {
                ledger.createAccount(tenantId,
                        new CreateAccountRequest(spec[0], spec[1], "BRC"));
            } catch (dev.brp.common.ConflictException alreadyThere) {
                // provisioning is idempotent
            }
        }
    }
}
