package dev.brp.mod;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BRP universal mod entry point.
 *
 * Single jar, two runtimes (see brp-mod/README.md):
 * - DEDICATED_SERVER -> start the Agent runtime (collect/probe/execute/queue)
 * - CLIENT           -> start the Client UI runtime (read-only, proxied writes)
 *
 * Dist detection happens here once; everything downstream receives a runtime
 * object and stays dist-agnostic. P1 fills in the actual event-bus wiring
 * (player tracking, container hooks, block registration for the BRP blocks
 * listed in docs/02-integrations.md section 6).
 */
@Mod(BrpMod.MOD_ID)
public final class BrpMod {

    public static final String MOD_ID = "brp";
    public static final Logger LOG = LoggerFactory.getLogger("BRP");

    public BrpMod() {
        switch (FMLEnvironment.dist) {
            case DEDICATED_SERVER -> LOG.info("[BRP] agent runtime starting (server side)");
            case CLIENT -> LOG.info("[BRP] client UI runtime starting (player side)");
            default -> LOG.info("[BRP] inert runtime (singleplayer/LAN; backend not applicable)");
        }
    }
}
