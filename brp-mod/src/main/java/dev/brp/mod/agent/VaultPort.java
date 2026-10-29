package dev.brp.mod.agent;

/**
 * The dispense port (docs/12 section 8): the mint window's step 2 asks the
 * WORLD to hand over bearer items. Pure Java — the NeoForge adapter does
 * the container spelunking. @return true only when every stack left the
 * vault; partial withdrawals are failures, not discounts.
 */
public interface VaultPort {

    boolean dispense(int stacks);
}
