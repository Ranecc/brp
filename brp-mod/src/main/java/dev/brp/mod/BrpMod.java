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
            case DEDICATED_SERVER -> LOG.info("[BRP] agent runtime starting (server side)");
            case CLIENT -> LOG.info("[BRP] client UI runtime starting (player side)");
            default -> LOG.info("[BRP] inert runtime (singleplayer/LAN; backend not applicable)");
        }
    }

    /**
     * P2-12: the agent boots on the DEDICATED server's lifecycle — guarded,
     * because these events also fire for the integrated (singleplayer)
     * server, where BRP is inert by design.
     */
    @EventBusSubscriber(modid = MOD_ID, value = Dist.DEDICATED_SERVER,
            bus = EventBusSubscriber.Bus.GAME)
    public static final class DedicatedOnly {
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
