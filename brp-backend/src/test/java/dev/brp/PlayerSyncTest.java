package dev.brp;

import dev.brp.repository.PlayerNameHistoryRepository;
import dev.brp.repository.PlayerRepository;
import dev.brp.service.PlayerService;
import dev.brp.dto.PlayerDtos.PlayerDto;
import dev.brp.dto.PlayerDtos.PlayerSyncRequest;
import dev.brp.dto.PlayerDtos.SyncAck;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Agent-pulled player identity sync: idempotent batches + rename history. */
@SpringBootTest
class PlayerSyncTest {

    @Autowired PlayerService sync;
    @Autowired PlayerRepository players;
    @Autowired PlayerNameHistoryRepository history;

    private PlayerSyncRequest batch(String batchId, String uuid, String name) {
        return new PlayerSyncRequest(batchId, List.of(new PlayerDto(uuid, name)));
    }

    @Test
    void duplicateBatchIdIsReplayedNotReapplied() {
        String uuid = UUID.randomUUID().toString();
        String batchId = UUID.randomUUID().toString();

        SyncAck first = sync.sync(batch(uuid, uuid, "Notch"));
        assertThat(first.inserted()).isEqualTo(1);
        long countAfterFirst = players.count();   // context is shared across tests

        SyncAck replay = sync.sync(batch(uuid, uuid, "Notch"));
        assertThat(replay).isEqualTo(first);           // stored response, no re-execution
        assertThat(players.count()).isEqualTo(countAfterFirst); // nothing re-applied
    }

    @Test
    void renamesAreRecordedAsHistory() {
        String uuid = UUID.randomUUID().toString();
        sync.sync(batch(UUID.randomUUID().toString(), uuid, "OldName"));
        SyncAck ack = sync.sync(batch(UUID.randomUUID().toString(), uuid, "NewName"));

        assertThat(ack.renamed()).isEqualTo(1);
        assertThat(players.findById(uuid).orElseThrow().getName()).isEqualTo("NewName");
        assertThat(history.count()).isEqualTo(1);
    }
}
