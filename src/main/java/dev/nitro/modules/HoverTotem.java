package dev.nitro.modules;

import dev.nitro.mixin.HandledScreenAccessor;
import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;

/**
 * Hover Totem: when the cursor rests on a totem in the player's inventory screen, move it immediately
 * to the configured destination. This is a client-side inventory convenience, not an anti-cheat bypass.
 */
public final class HoverTotem extends Module {
    public static HoverTotem INSTANCE;
    public enum Destination {
        OFFHAND("Offhand"), HOTBAR("Hotbar slot");
        private final String label;
        Destination(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    private final Setting.Choice<Destination> destination =
            new Setting.Choice<>("Destination", Destination.OFFHAND, Destination.class);
    private final Setting.Num hotbarSlot = new Setting.Num("Hotbar slot (1-9)", 1, 1, 9, 1);
    private final Setting.Bool hud = new Setting.Bool("Totem count HUD", true);

    private int lastHoveredSlot = -1;
    private int cooldown;

    public HoverTotem() {
        super("Hover Totem", "Hover a totem in your inventory to move it to your offhand or a selected hotbar slot.", Category.COMBAT);
        add(destination, hotbarSlot, hud);
        hotbarSlot.showIf(() -> destination.value == Destination.HOTBAR);
        INSTANCE = this;
    }


    /** Lets Auto Totem avoid undoing the selected hotbar destination while the inventory is open. */
    public static boolean isHotbarDestinationActive() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.destination.value == Destination.HOTBAR;
    }

    @Override
    public void onEnable() {
        lastHoveredSlot = -1;
        cooldown = 0;
    }

    @Override
    public void onDisable() {
        lastHoveredSlot = -1;
        cooldown = 0;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.interactionManager == null) return;
        if (!(mc.currentScreen instanceof InventoryScreen screen)) {
            lastHoveredSlot = -1;
            cooldown = 0;
            return;
        }

        if (cooldown > 0) cooldown--;

        Slot slot = ((HandledScreenAccessor) screen).nitro$getFocusedSlot();
        if (slot == null || !slot.hasStack() || !slot.getStack().isOf(Items.TOTEM_OF_UNDYING)
                || slot.id < 9 || slot.id > 44) {
            lastHoveredSlot = -1;
            return;
        }

        // Only trigger once per hover. Moving away and hovering the slot again re-arms the action.
        if (slot.id == lastHoveredSlot || cooldown > 0) return;

        int swapButton;
        if (destination.value == Destination.OFFHAND) {
            swapButton = 40; // ScreenHandler SWAP button 40 is the offhand.
        } else {
            swapButton = hotbarSlot.i() - 1; // SWAP buttons 0..8 address hotbar slots 1..9.
            int targetHandlerSlot = 36 + swapButton;
            if (slot.id == targetHandlerSlot) {
                lastHoveredSlot = slot.id;
                return;
            }
        }

        mc.interactionManager.clickSlot(
                mc.player.playerScreenHandler.syncId,
                slot.id,
                swapButton,
                SlotActionType.SWAP,
                mc.player
        );
        lastHoveredSlot = slot.id;
        cooldown = 3;
    }

    @Override
    public void renderHud(DrawContext ctx) {
        if (!hud.on() || mc.player == null) return;
        int count = mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING) ? 1 : 0;
        for (ItemStack stack : mc.player.getInventory().main) {
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) count += stack.getCount();
        }
        int x = ctx.getScaledWindowWidth() - 76;
        int y = 12;
        ctx.fill(x, y, x + 64, y + 28, 0xA91A1028);
        ctx.fill(x, y, x + 64, y + 1, 0xFFB56CFF);
        ctx.drawItem(new ItemStack(Items.TOTEM_OF_UNDYING), x + 5, y + 5);
        ctx.drawText(mc.textRenderer, "x" + count, x + 27, y + 10,
                count > 0 ? 0xFFFFE8A3 : 0xFFFF7777, true);
    }
}
