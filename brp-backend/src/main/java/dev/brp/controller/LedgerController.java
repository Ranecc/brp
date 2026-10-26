package dev.brp.controller;
import dev.brp.service.*;

import dev.brp.contract.LedgerDtos.AccountDto;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.contract.LedgerDtos.PostJournalResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}")
public class LedgerController {

    private final LedgerService ledger;

    public LedgerController(LedgerService ledger) {
        this.ledger = ledger;
    }

    @PostMapping("/accounts")
    public AccountDto createAccount(@PathVariable String tenantId,
                                    @Valid @RequestBody CreateAccountRequest req) {
        return ledger.createAccount(tenantId, req);
    }

    @GetMapping("/accounts")
    public List<AccountDto> listAccounts(@PathVariable String tenantId) {
        return ledger.listAccounts(tenantId);
    }

    /** Command plane write: idempotencyKey travels in the body (agent-generated, docs/06 section 2). */
    @PostMapping("/journal")
    public PostJournalResponse postJournal(@PathVariable String tenantId,
                                           @Valid @RequestBody PostJournalRequest req) {
        return ledger.post(tenantId, req);
    }
}
