package dev.brp.mod.block;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * Native-MC form picker (docs/09 section 2). Each pending form is a sheet
 * of paper in the tray; clicking one selects it server-side (menus are
 * server-authority — clients NEVER write state, they click paper like
 * everyone else). The live form list arrives with P2-10's agent fetch;
 * until then the desk shows the shapes the paperwork will take.
 */
public class FormTerminalMenu extends AbstractContainerMenu {

    /** A pending form as the desk sees it. P2-10 replaces this stub feed. */
    public record PendingForm(String formKey, String title, String submitter) {}

    private static final int TRAY_SIZE = 6;

    private final List<PendingForm> forms;
    private int selected = -1;

    public FormTerminalMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, List.of(
                new PendingForm("approval.withdrawal", "Withdrawal Request", "officer"),
                new PendingForm("economy.repeg.vote", "Re-peg Vote", "owner"),
                new PendingForm("wms.transfer", "Warehouse Transfer", "member")));
    }

    public FormTerminalMenu(int containerId, Inventory playerInventory, List<PendingForm> forms) {
        super(BrpBlocks.FORM_TERMINAL_MENU.get(), containerId);
        this.forms = List.copyOf(forms);

        SimpleContainer tray = new SimpleContainer(TRAY_SIZE);
        for (int i = 0; i < TRAY_SIZE; i++) {
            if (i < this.forms.size()) {
                ItemStack paper = new ItemStack(Items.PAPER);
                paper.set(DataComponents.CUSTOM_NAME, Component.literal(this.forms.get(i).title()));
                tray.setItem(i, paper);
            }
            final int index = i;
            addSlot(new Slot(tray, i, 26 + (i % 3) * 18, 20 + (i / 3) * 18) {
                @Override public boolean mayPlace(ItemStack stack) { return false; }
                @Override public boolean mayPickup(Player player) { return false; }
                @Override
                public ItemStack getItem() {
                    ItemStack sheet = super.getItem();
                    if (!sheet.isEmpty() && index == selected) {
                        ItemStack marked = sheet.copyWithCount(1);
                        marked.set(DataComponents.CUSTOM_NAME, Component.literal(
                                FormTerminalMenu.this.forms.get(index).title() + " ✓"));
                        return marked;
                    }
                    return sheet;
                }
            });
        }
    }

    @Override
    public void clicked(int slot, int button, ClickType type, Player player) {
        if (type == ClickType.PICKUP && slot >= 0 && slot < forms.size()) {
            selected = slot;   // selection only: paper stays in the tray
            return;
        }
        super.clicked(slot, button, type, player);
    }

    /** @return the form the player pointed at, or null (the desk stays calm). */
    public PendingForm selectedForm() {
        return selected >= 0 && selected < forms.size() ? forms.get(selected) : null;
    }

    public List<PendingForm> forms() {
        return forms;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;   // forms are filed, not carried
    }

    @Override
    public boolean stillValid(Player player) {
        return true;             // the desk does not expire while you read it
    }
}
