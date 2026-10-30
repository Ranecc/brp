package dev.brp.mod.agent;

import java.util.SortedMap;

/**
 * The bourse's physical desk (docs/14 §8, P5-10): one bound container is
 * the intake/withdraw slot. Pure Java — the NeoForge adapter does the
 * container spelunking on the right thread.
 */
public interface BoursePort {

    /** @return itemId -> total count currently in the container, sorted. */
    SortedMap<String, Long> inventory();

    /** @return true only when the full quantity left the container. */
    boolean extract(String itemId, long qty);

    /** @return true only when the full quantity fit into the container. */
    boolean deposit(String itemId, long qty);
}
