package dev.brp.service;

import dev.brp.dto.PlayerDtos.PlayerSyncRequest;
import dev.brp.dto.PlayerDtos.SyncAck;
import dev.brp.dto.PlayerResponse;

/** Player identity domain contract (impl: {@link PlayerServiceImpl}). */
public interface PlayerService {

    SyncAck sync(PlayerSyncRequest req);

    PlayerResponse byUuid(String uuid);
}
