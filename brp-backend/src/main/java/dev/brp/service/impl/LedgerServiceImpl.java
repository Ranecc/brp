package dev.brp.service.impl;
import dev.brp.entity.*;
import dev.brp.repository.*;
import dev.brp.service.CurrencyService;
import dev.brp.service.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.brp.common.BadRequestException;
import dev.brp.common.ConflictException;
import dev.brp.contract.LedgerDtos.AccountDto;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.contract.LedgerDtos.PostJournalResponse;
import dev.brp.mapper.AccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Double-entry ledger core (docs/06 section 5). The idempotency row is the
 * LAST insert of the work transaction; concurrent duplicates lose the PK race,
 * roll back entirely, and replay the winner's stored response.
 */
@Service
public class LedgerServiceImpl implements LedgerService {

    private static final Logger log = LoggerFactory.getLogger(LedgerServiceImpl.class);

    private final AccountRepository accounts;
    private final JournalEntryRepository journal;
    private final IdempotencyKeyRepository keys;
    private final CurrencyService currencies;
    private final AccountMapper accountMapper;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public LedgerServiceImpl(AccountRepository accounts, JournalEntryRepository journal,
                             IdempotencyKeyRepository keys, CurrencyService currencies,
                             AccountMapper accountMapper,
                             ObjectMapper mapper, TransactionTemplate tx) {
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
            throw new ConflictException("account already exists: " + req.accountNo());
        });
        try {
            var saved = tx.execute(status ->
                    accounts.save(new AccountEntity(tenantId, req.accountNo(),
                            req.accountType(), req.currency())));
            return accountMapper.toDto(saved);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("account already exists: " + req.accountNo());
        }
    }

    @Override
    public PostJournalResponse post(String tenantId, PostJournalRequest req) {
        var existing = keys.findById(req.idempotencyKey());
        if (existing.isPresent()) {
            return replay(existing.get());
        }
        validate(tenantId, req);
        try {
            return tx.execute(status -> doPost(tenantId, req));
        } catch (DataIntegrityViolationException lost) {
            var winner = keys.findById(req.idempotencyKey()).orElse(null);
            if (winner == null) {
                throw new IllegalStateException("idempotency row missing after unique violation", lost);
            }
            return replay(winner);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConflictException(
                    "concurrent update on the same account, retry with the SAME idempotency key");
        }
    }

    @Override
    public List<AccountDto> listAccounts(String tenantId) {
        return accounts.findByTenantIdOrderById(tenantId).stream()
                .map(accountMapper::toDto).toList();
    }

    // --- internals (unchanged semantics, moved verbatim from the former class) ---

    private PostJournalResponse doPost(String tenantId, PostJournalRequest req) {
        Map<String, AccountEntity> byNo = loadAccounts(tenantId, req.lines());
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
        keys.save(new IdempotencyKeyEntity(req.idempotencyKey(), tenantId, toJson(response)));
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
        boolean dirsOk = req.lines().stream()
                .allMatch(l -> "DEBIT".equals(l.direction()) || "CREDIT".equals(l.direction()));
        if (!dirsOk) {
            throw new BadRequestException("direction must be DEBIT or CREDIT");
        }
        long debit = req.lines().stream()
                .filter(l -> "DEBIT".equals(l.direction())).mapToLong(JournalLine::amountCents).sum();
        long credit = req.lines().stream()
                .filter(l -> "CREDIT".equals(l.direction())).mapToLong(JournalLine::amountCents).sum();
        if (debit != credit || debit == 0) {
            throw new BadRequestException(
                    "unbalanced journal: debit=%d credit=%d".formatted(debit, credit));
        }
        if (req.lines().stream().anyMatch(l ->
                accounts.findByTenantIdAndAccountNo(tenantId, l.accountNo()).isEmpty())) {
            throw new BadRequestException("journal references unknown account in tenant");
        }
    }

    private void requireTradableCurrency(String code) {
        if (!currencies.isTradable(code)) {
            throw dev.brp.common.BadRequestException.of("economy.currency.unknown", code);
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
