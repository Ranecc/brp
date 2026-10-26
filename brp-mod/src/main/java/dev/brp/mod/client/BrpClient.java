package dev.brp.mod.client;

import dev.brp.mod.BrpMod;
import dev.brp.mod.block.BrpBlocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only registrations. Screen factories live here so the server
 * dist never loads a GUI class — one jar, two runtimes, zero accidents.
 */
@EventBusSubscriber(modid = BrpMod.MOD_ID, value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class BrpClient {

    private BrpClient() {}

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(BrpBlocks.FORM_TERMINAL_MENU.get(), FormTerminalScreen::new);
    }
}
