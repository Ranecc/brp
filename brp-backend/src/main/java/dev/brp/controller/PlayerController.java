package dev.brp.controller;
import dev.brp.service.*;

import dev.brp.dto.PlayerDtos.PlayerSyncRequest;
import dev.brp.dto.PlayerDtos.SyncAck;
import dev.brp.dto.PlayerResponse;
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
public class PlayerController {

    private final PlayerService playerService;

    public PlayerController(PlayerService playerService) {
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
