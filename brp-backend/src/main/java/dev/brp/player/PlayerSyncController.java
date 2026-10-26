package dev.brp.player;

import dev.brp.player.dto.PlayerDtos.PlayerSyncRequest;
import dev.brp.player.dto.PlayerDtos.SyncAck;
import dev.brp.player.dto.PlayerResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Thin transport layer: validation in, service call, DTO out. */
@RestController
@RequestMapping("/api/v1/players")
public class PlayerSyncController {

    private final PlayerService playerService;

    public PlayerSyncController(PlayerService playerService) {
        this.playerService = playerService;
    }

    @PostMapping("/batch")
    public SyncAck sync(@Valid @RequestBody PlayerSyncRequest req) {
        return playerService.sync(req);
    }

    @GetMapping("/{uuid}")
    public PlayerResponse byUuid(@PathVariable String uuid) {
        return playerService.byUuid(uuid);
    }
}
