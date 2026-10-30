package dev.brp.mod.agent;

/**
 * The chunk actuator port (docs/16 §3, P6): the mod holds chunk tickets
 * itself, so registered lines tick with ZERO players in range — no fake
 * players, no AFK pools. Pure Java; the NeoForge adapter does the ticket
 * spelunking on the server thread.
 *
 * Only tickets the MOD placed are managed here: releasing a ticket the
 * operator placed with /forceload is not our business.
 */
public interface ChunkActuator {

    /** @return true when the mod now holds the ticket (or already did). */
    boolean activate(String dimension, String chunkKey);

    /** @return true when the mod's ticket was released (false = it wasn't ours). */
    boolean deactivate(String dimension, String chunkKey);

    /** @return the keys of tickets the mod itself is holding. */
    java.util.Set<String> held();
}
