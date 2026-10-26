package dev.brp.mod.adapters;

import java.util.List;

/**
 * FTB Teams compat adapter (docs/07-tenants.md). Sketch — wiring lands with
 * the P1 agent build; entry points researched 2026-10 (◆ verify signatures
 * against the current ftb-teams source at wiring time):
 *
 * Dependency: maven.ftb.dev.org, dev.ftb:ftb-teams-neoforge (1.21.1 line).
 * Roster walk:
 *   FTBTeamsAPI.api().getManager()  -> TeamManager
 *   manager.getTeamByID(uuid) / getTeamByPlayer(uuid) -> Team
 *   PartyTeam (player-run guilds)   -> getMembers() -> TeamMember (uuid, role)
 *   role mapping: FTB owner -> OWNER, officer -> OFFICER, else MEMBER
 * Team ID stability: use the FTB team UUID as the backend team id (stable
 * across renames); name/tag mirrored per sync batch.
 * Notes:
 *   - FTB "Player Teams" (solo) are skipped; only parties are guilds.
 *   - offline-mode servers: FTB still tracks profiles — uuids flow verbatim
 *     into the backend (same policy as player sync).
 *   - in compat mode the agent NEVER mutates FTB state; sync is read-only.
 */
public final class FtbTeamsAdapter {

    private FtbTeamsAdapter() {}

    public static final String SOURCE = "FTBTEAMS";

    @SuppressWarnings("unused")
    private static final class Sketch {
        static List<?> EMPTY = List.of();
    }
}
