package dev.brp.service.impl;
import dev.brp.entity.*;
import dev.brp.repository.*;
import dev.brp.service.*;

import dev.brp.common.annotation.Atomic;
import dev.brp.common.annotation.Cached;
import dev.brp.common.annotation.Idempotent;
import dev.brp.dto.PlayerDtos.PlayerDto;
import dev.brp.dto.PlayerDtos.PlayerSyncRequest;
import dev.brp.dto.PlayerDtos.SyncAck;
import dev.brp.dto.PlayerResponse;
import dev.brp.mapper.PlayerMapper;
import org.springframework.stereotype.Service;

/**
 * Player identity sync pulled from the MC server (docs/07 section 5):
 * idempotent per batch, atomic, lookups cached. Cross-cutting annotations sit
 * on the service layer where the semantics live.
 */
@Service
public class PlayerServiceImpl implements PlayerService {

    private final PlayerRepository players;
    private final PlayerNameHistoryRepository history;
    private final PlayerMapper mapper;

    public PlayerServiceImpl(PlayerRepository players,
                             PlayerNameHistoryRepository history,
                             PlayerMapper mapper) {
        this.players = players;
        this.history = history;
        this.mapper = mapper;
    }

    @Override
    @Idempotent(key = "#req.batchId()")
    @Atomic
    public SyncAck sync(PlayerSyncRequest req) {
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

    @Override
    @Cached(key = "#uuid", ttlSeconds = 300)
    public PlayerResponse byUuid(String uuid) {
        return players.findById(uuid).map(mapper::toDto).orElse(null);
    }
}
