package dev.brp.player;

import dev.brp.common.annotation.Atomic;
import dev.brp.common.annotation.Cached;
import dev.brp.common.annotation.Idempotent;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Player identity sync, pulled from the Minecraft server by the agent
 * (uuid <-> current name, rename history preserved). Dogfoods the annotation
 * kit: idempotent on batchId, atomic per batch, cached lookups.
 */
@RestController
@RequestMapping("/api/v1/players")
public class PlayerSyncController {

    private final PlayerRepository players;
    private final PlayerNameHistoryRepository history;

    public PlayerSyncController(PlayerRepository players,
                                PlayerNameHistoryRepository history) {
        this.players = players;
        this.history = history;
    }

    public record PlayerDto(@NotBlank String uuid, @NotBlank String name) {}

    public record PlayerSyncRequest(
            @NotBlank String batchId,
            @NotEmpty @Valid List<PlayerDto> players) {}

    public record SyncAck(int inserted, int renamed, int refreshed) {}

    @PostMapping("/batch")
    @Idempotent(key = "#req.batchId()")
    @Atomic
    public SyncAck sync(@Valid @RequestBody PlayerSyncRequest req) {
        int inserted = 0, renamed = 0, refreshed = 0;
        for (PlayerDto dto : req.players()) {
            PlayerEntity existing = players.findById(dto.uuid()).orElse(null);
            if (existing == null) {
                players.save(new PlayerEntity(dto.uuid(), dto.name()));
                inserted++;
            } else if (!existing.getName().equals(dto.name())) {
                history.save(new PlayerNameHistoryRow(
                        dto.uuid(), existing.getName(), dto.name()));
                existing.renameTo(dto.name());
                players.save(existing);
                renamed++;
            } else {
                existing.touch();
                players.save(existing);
                refreshed++;
            }
        }
        return new SyncAck(inserted, renamed, refreshed);
    }

    @GetMapping("/{uuid}")
    @Cached(key = "#uuid", ttlSeconds = 300)
    public PlayerEntity byUuid(@PathVariable String uuid) {
        return players.findById(uuid).orElse(null);
    }
}
