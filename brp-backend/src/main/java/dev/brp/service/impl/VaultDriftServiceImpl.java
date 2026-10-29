package dev.brp.service.impl;

import dev.brp.entity.VaultRegistrationEntity;
import dev.brp.entity.VaultMovementEntity;
import dev.brp.repository.InventoryRepository;
import dev.brp.repository.VaultMovementRepository;
import dev.brp.repository.VaultRegistrationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The vault may not disagree with the ledger (docs/12 §8): hourly, every
 * registered container's cumulative books (OUT minus REVOKE) are checked
 * against the agent's latest inventory snapshot. A mismatch files BRP-0904
 * — physical stock is the vault's opinion; the books have the final word,
 * but the disagreement gets paperwork.
 */
@Service
public class VaultDriftServiceImpl {

    private final VaultRegistrationRepository registrations;
    private final VaultMovementRepository movements;
    private final InventoryRepository inventory;
    private final PegServiceImpl peg;

    public VaultDriftServiceImpl(VaultRegistrationRepository registrations,
                                 VaultMovementRepository movements,
                                 InventoryRepository inventory,
                                 PegServiceImpl peg) {
        this.registrations = registrations;
        this.movements = movements;
        this.inventory = inventory;
        this.peg = peg;
    }

    /** @return one line per drifted container; empty list = books and world agree. */
    public List<String> driftCheck() {
        List<String> drifted = new ArrayList<>();
        for (VaultRegistrationEntity reg : registrations.findAll()) {
            long book = movements
                    .findByTenantIdAndContainerRefAndItemId(
                            reg.getTenantId(), reg.getContainerRef(), reg.getItemId())
                    .stream()
                    .mapToLong(m -> "OUT".equals(m.getDirection()) ? m.getQty() : -m.getQty())
                    .sum();
            var snapshot = inventory.findByTenantIdAndContainerIdAndItemId(
                    reg.getTenantId(), reg.getContainerRef(), reg.getItemId());
            if (snapshot.isEmpty()) {
                continue;   // no agent report yet: nothing to disagree with
            }
            long diff = snapshot.get().getAmount() - book;
            if (diff != 0) {
                peg.file(904L, diff);
                drifted.add(reg.getContainerRef() + " off by " + diff
                        + " (" + reg.getItemId() + ")");
            }
        }
        return drifted;
    }

    @Scheduled(fixedDelayString = "${brp.vault.drift-ms:3600000}")
    public void scheduled() {
        driftCheck();
    }
}
