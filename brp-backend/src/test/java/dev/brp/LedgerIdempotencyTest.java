package dev.brp;

import dev.brp.common.BadRequestException;
import dev.brp.contract.LedgerDtos.CreateAccountRequest;
import dev.brp.contract.LedgerDtos.JournalLine;
import dev.brp.contract.LedgerDtos.PostJournalRequest;
import dev.brp.contract.LedgerDtos.PostJournalResponse;
import dev.brp.ledger.LedgerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the two guarantees from docs/06-transactions.md on the ledger:
 * 1. duplicate idempotency keys replay the original response with no double effect;
 * 2. unbalanced journals are rejected atomically.
 */
@SpringBootTest
class LedgerIdempotencyTest {

    @Autowired LedgerService ledger;

    private String tenant() {
        return "t-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void duplicateKeyReplaysOriginalResponseWithoutDoublePosting() {
        String tenant = tenant();
        ledger.createAccount(tenant, new CreateAccountRequest("alice", "PLAYER", "BRC"));
        ledger.createAccount(tenant, new CreateAccountRequest("faucet", "SYSTEM_FAUCET", "BRC"));

        var request = new PostJournalRequest("idem-" + UUID.randomUUID(), "BRC", List.of(
                new JournalLine("alice", "DEBIT", 10_000L, "daily reward"),
                new JournalLine("faucet", "CREDIT", 10_000L, "daily reward")));

        PostJournalResponse first = ledger.post(tenant, request);
        assertThat(first.replayed()).isFalse();

        PostJournalResponse replay = ledger.post(tenant, request);
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.journalId()).isEqualTo(first.journalId());

        var alice = ledger.listAccounts(tenant).stream()
                .filter(a -> a.accountNo().equals("alice")).findFirst().orElseThrow();
        // credited exactly once: +10000
        assertThat(alice.balanceCents()).isEqualTo(10_000L);
    }

    @Test
    void distinctKeysPostDistinctJournals() {
        String tenant = tenant();
        ledger.createAccount(tenant, new CreateAccountRequest("alice", "PLAYER", "BRC"));
        ledger.createAccount(tenant, new CreateAccountRequest("faucet", "SYSTEM_FAUCET", "BRC"));

        var body = List.of(
                new JournalLine("alice", "DEBIT", 500L, null),
                new JournalLine("faucet", "CREDIT", 500L, null));

        var first = ledger.post(tenant, new PostJournalRequest("idem-" + UUID.randomUUID(), "BRC", body));
        var second = ledger.post(tenant, new PostJournalRequest("idem-" + UUID.randomUUID(), "BRC", body));

        assertThat(first.journalId()).isNotEqualTo(second.journalId());
        var alice = ledger.listAccounts(tenant).stream()
                .filter(a -> a.accountNo().equals("alice")).findFirst().orElseThrow();
        assertThat(alice.balanceCents()).isEqualTo(1000L);
    }

    @Test
    void unbalancedJournalIsRejected() {
        String tenant = tenant();
        ledger.createAccount(tenant, new CreateAccountRequest("alice", "PLAYER", "BRC"));
        ledger.createAccount(tenant, new CreateAccountRequest("faucet", "SYSTEM_FAUCET", "BRC"));

        var unbalanced = new PostJournalRequest("idem-" + UUID.randomUUID(), "BRC", List.of(
                new JournalLine("alice", "DEBIT", 100L, null),
                new JournalLine("faucet", "CREDIT", 99L, null)));

        assertThatThrownBy(() -> ledger.post(tenant, unbalanced))
                .isInstanceOf(BadRequestException.class);

        // rejection must leave no trace: no balance change, no idempotency row
        var alice = ledger.listAccounts(tenant).stream()
                .filter(a -> a.accountNo().equals("alice")).findFirst().orElseThrow();
        assertThat(alice.balanceCents()).isZero();
    }
}
