package dev.brp.mod;

import dev.brp.mod.agent.BrpAgent;
import dev.brp.mod.block.BrpBlocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * BRP universal mod entry point.
 *
 * Single jar, two runtimes (see brp-mod/README.md):
 * - DEDICATED_SERVER -> start the Agent runtime (collect/probe/execute/queue)
 * - CLIENT           -> start the Client UI runtime (read-only, proxied writes)
 *                       AND, since P6, the agent itself — singleplayer gets
 *                       the full local ERP (sidecar backend on H2, line
 *                       gates, the ticket desk) because production-line
 *                       management belongs to the person playing, not just
 *                       to server admins.
 *                       Opt out with -Dbrp.agent.enabled=false.
 *
 * Dist detection happens here once; everything downstream receives a runtime
 * object and stays dist-agnostic. P2 adds the shop-floor terminals
 * (docs/09): registration runs on the mod bus for BOTH dists — the server
 * must know the blocks exist before it can govern them.
 */
@Mod(BrpMod.MOD_ID)
public final class BrpMod {

    public static final String MOD_ID = "brp";
    public static final Logger LOG = LoggerFactory.getLogger("BRP");

    public BrpMod(IEventBus modBus) {
        BrpBlocks.register(modBus);
        switch (FMLEnvironment.dist) {
            case DEDICATED_SERVER -> LOG.info("[BRP] agent runtime starting (dedicated server)");
            case CLIENT -> LOG.info("[BRP] client runtime starting (singleplayer/LAN: local ERP mode)");
            default -> LOG.info("[BRP] inert runtime");
        }
    }

    /**
     * P6: the agent boots on BOTH dists' server lifecycle — the integrated
     * server is a real server to the ACL, and the supervisor's sidecar is a
     * separate JVM (red line 1 holds: Spring never enters the game JVM).
     */
    @EventBusSubscriber(modid = MOD_ID, value = {Dist.DEDICATED_SERVER, Dist.CLIENT},
            bus = EventBusSubscriber.Bus.GAME)
    public static final class ServerLifecycle {
        @SubscribeEvent
        public static void onServerStarting(ServerStartingEvent event) {
            BrpAgent.boot(event.getServer());
        }

        @SubscribeEvent
        public static void onServerStopping(ServerStoppingEvent event) {
            BrpAgent.shutdown();
        }
    }
}
