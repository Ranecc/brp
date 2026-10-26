package dev.brp.service;

import dev.brp.contract.LedgerDtos.AccountDto;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.contract.LedgerDtos.PostJournalResponse;

import java.util.List;

/** Ledger domain contract (impl: {@link LedgerServiceImpl}). */
public interface LedgerService {

    AccountDto createAccount(String tenantId, CreateAccountRequest req);

    PostJournalResponse post(String tenantId, PostJournalRequest req);

    List<AccountDto> listAccounts(String tenantId);
}
