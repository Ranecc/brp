package dev.brp.mod.client;

import dev.brp.mod.block.FormTerminalMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The form desk, native Minecraft chrome (docs/09 section 2): vanilla
 * container screen, vanilla slots, vanilla paper. Tooltips come free from
 * AbstractContainerScreen. Live form data and agent-side submission wire
 * up in P2-10; the desk is real, the paperwork is en route.
 */
public class FormTerminalScreen extends AbstractContainerScreen<FormTerminalMenu> {

    private static final int TRIM = 0xFF3FB950;   // creeper green, obviously
    private static final int PANEL = 0xC0101014;  // deepslate darkness

    public FormTerminalScreen(FormTerminalMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 176;
        this.imageHeight = 80;
        this.inventoryLabelY = -9999;   // no player inventory row on this desk
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partial, int mouseX, int mouseY) {
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, PANEL);
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + 2, TRIM);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partial) {
        super.render(gfx, mouseX, mouseY, partial);
        gfx.drawString(font, this.title, leftPos + 8, topPos + 8, TRIM, true);
        gfx.drawString(font, Component.translatable("screen.brp.form_terminal.hint"),
                leftPos + 8, topPos + 66, 0xFF8B949E, false);
        renderTooltip(gfx, mouseX, mouseY);
    }
}
