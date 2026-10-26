package dev.brp.mod.spi;

/**
 * Adapter registration entry point.
 *
 * SPI rule (docs/02-integrations.md section 7): NOTHING in this package may
 * import Minecraft or other-mod classes. The agent core and all business logic
 * compile against these interfaces only; per-version/per-mod specifics stay in
 * the adapters package. This is what keeps the 26.x port cheap later.
 */
public interface BrpAdapter {

    /** Mod id of the integration target, e.g. "ae2", "create", "minecraft". */
    String targetModId();

    /** Higher priority wins when several adapters claim the same container/machine. */
    int priority();

    default boolean isActive() {
        return true;
    }
}
