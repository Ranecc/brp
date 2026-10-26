package dev.brp.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.brp.common.BadRequestException;
import dev.brp.common.ConflictException;
import dev.brp.contract.LedgerDtos.AccountDto;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.contract.LedgerDtos.PostJournalResponse;
import dev.brp.entity.*;
import dev.brp.repository.*;
import dev.brp.service.CurrencyService;
import dev.brp.service.LedgerService;
import dev.brp.mapper.AccountMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Double-entry ledger core (docs/06 section 5). Money path: aggressively
 * boring, TENANT-SCOPED idempotency (V9 composite PK (tenantId, idemKey) —
 * the same key in another tenant is a different operation), narrow exception
 * handling (only OUR key race replays; other integrity violations surface),
 * currency-matched legs, service-side positivity guard, TOCTOU as 400.
 */
@Service
public class LedgerServiceImpl implements LedgerService {

    private final AccountRepository accounts;
    private final JournalEntryRepository journal;
    private final IdempotencyKeyRepository keys;
    private final CurrencyService currencies;
    private final AccountMapper accountMapper;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public LedgerServiceImpl(AccountRepository accounts, JournalEntryRepository journal,
                             IdempotencyKeyRepository keys, CurrencyService currencies,
                             AccountMapper accountMapper, ObjectMapper mapper,
                             TransactionTemplate tx) {
        this.accounts = accounts;
        this.journal = journal;
        this.keys = keys;
        this.currencies = currencies;
        this.accountMapper = accountMapper;
        this.mapper = mapper;
        this.tx = tx;
    }

    @Override
    public AccountDto createAccount(String tenantId, CreateAccountRequest req) {
        requireTradableCurrency(req.currency());
        accounts.findByTenantIdAndAccountNo(tenantId, req.accountNo()).ifPresent(a -> {
            throw ConflictException.of("ledger.account.duplicate");
        });
        try {
            var saved = tx.execute(status ->
                    accounts.save(new AccountEntity(tenantId, req.accountNo(),
                            req.accountType(), req.currency())));
            return accountMapper.toDto(saved);
        } catch (DataIntegrityViolationException e) {
            // Only our unique constraint means "already exists"; anything else
            // (future FK, column length) propagates with the real cause.
            if (accounts.findByTenantIdAndAccountNo(tenantId, req.accountNo()).isPresent()) {
                throw ConflictException.of("ledger.account.duplicate");
            }
            throw e;
        }
    }

    @Override
    public PostJournalResponse post(String tenantId, PostJournalRequest req) {
        // Fast path: replay ONLY this tenant's stored response (V9).
        var existing = keys.findById(new IdempotencyKeyEntity.Pk(tenantId, req.idempotencyKey()));
        if (existing.isPresent()) {
            return replay(existing.get());
        }
        validate(tenantId, req);
        try {
            return tx.execute(status -> doPost(tenantId, req));
        } catch (DataIntegrityViolationException e) {
            // Key race in OUR tenant -> replay the winner; anything else rethrows.
            var winner = keys.findById(new IdempotencyKeyEntity.Pk(tenantId, req.idempotencyKey()))
                    .orElseThrow(() -> e);
            return replay(winner);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw ConflictException.of("ledger.concurrent.retry");
        }
    }

    @Override
    public List<AccountDto> listAccounts(String tenantId) {
        return accounts.findByTenantIdOrderById(tenantId).stream()
                .map(accountMapper::toDto).toList();
    }

    // --- internals ---

    private PostJournalResponse doPost(String tenantId, PostJournalRequest req) {
        Map<String, AccountEntity> byNo;
        try {
            byNo = loadAccounts(tenantId, req.lines());
        } catch (NoSuchElementException e) {
            // account vanished between validate() and here (TOCTOU): 400, not 500
            throw BadRequestException.of("ledger.account.unknown");
        }
        List<AccountEntity> touched = new ArrayList<>();
        for (JournalLine line : req.lines()) {
            AccountEntity acc = byNo.get(line.accountNo());
            acc.apply(signed(line));
            touched.add(acc);
        }
        String journalId = UUID.randomUUID().toString();
        for (JournalLine line : req.lines()) {
            journal.save(new JournalEntryEntity(journalId, req.idempotencyKey(), tenantId,
                    byNo.get(line.accountNo()).getId(), line.direction(),
                    line.amountCents(), req.currency(), line.memo()));
        }
        var response = new PostJournalResponse(journalId, false,
                touched.stream().map(accountMapper::toDto).toList());
        keys.save(new IdempotencyKeyEntity(tenantId, req.idempotencyKey(), toJson(response)));
        return response;
    }

    private PostJournalResponse replay(IdempotencyKeyEntity key) {
        try {
            var original = mapper.readValue(key.getResponseJson(), PostJournalResponse.class);
            return new PostJournalResponse(original.journalId(), true, original.affectedAccounts());
        } catch (JsonProcessingException | NullPointerException e) {
            throw new IllegalStateException("corrupted idempotency snapshot: " + key.getIdemKey(), e);
        }
    }

    private void validate(String tenantId, PostJournalRequest req) {
        requireTradableCurrency(req.currency());
        if (req.lines() == null || req.lines().isEmpty()) {
            throw BadRequestException.of("ledger.lines.empty");
        }
        boolean dirsOk = req.lines().stream()
                .allMatch(l -> "DEBIT".equals(l.direction()) || "CREDIT".equals(l.direction()));
        if (!dirsOk) {
            throw BadRequestException.of("ledger.direction.invalid");
        }
        long debit = 0, credit = 0;
        for (JournalLine l : req.lines()) {
            if (l.amountCents() == null || l.amountCents() <= 0) {
                // negative + balanced = silent reverse booking; not on our watch
                throw BadRequestException.of("ledger.amount.positive");
            }
            if ("DEBIT".equals(l.direction())) debit += l.amountCents();
            else credit += l.amountCents();
        }
        if (debit != credit || debit == 0) {
            throw BadRequestException.of("ledger.unbalanced");
        }
        for (JournalLine l : req.lines()) {
            var acc = accounts.findByTenantIdAndAccountNo(tenantId, l.accountNo());
            if (acc.isEmpty()) {
                throw BadRequestException.of("ledger.account.unknown");
            }
            // cross-currency legs are a laundering vector, not a feature
            if (!req.currency().equals(acc.get().getCurrency())) {
                throw BadRequestException.of("ledger.currency.mismatch");
            }
        }
    }

    private void requireTradableCurrency(String code) {
        if (!currencies.isTradable(code)) {
            throw BadRequestException.of("economy.currency.unknown", code);
        }
    }

    private Map<String, AccountEntity> loadAccounts(String tenantId, List<JournalLine> lines) {
        return lines.stream()
                .map(JournalLine::accountNo)
                .distinct()
                .map(no -> accounts.findByTenantIdAndAccountNo(tenantId, no).orElseThrow())
                .collect(Collectors.toMap(AccountEntity::getAccountNo, Function.identity()));
    }

    private long signed(JournalLine line) {
        return "DEBIT".equals(line.direction()) ? line.amountCents() : -line.amountCents();
    }

    private String toJson(PostJournalResponse response) {
        try {
            return mapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
