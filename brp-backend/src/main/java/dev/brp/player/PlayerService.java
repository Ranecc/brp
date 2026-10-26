package dev.brp.player;

import dev.brp.player.dto.PlayerDtos.PlayerSyncRequest;
import dev.brp.player.dto.PlayerDtos.SyncAck;
import dev.brp.player.dto.PlayerResponse;

/** Player identity domain contract (impl: {@link PlayerServiceImpl}). */
public interface PlayerService {

    SyncAck sync(PlayerSyncRequest req);

    PlayerResponse byUuid(String uuid);
}
