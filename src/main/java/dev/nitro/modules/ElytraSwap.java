package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Elytra Swap: bind a key to it. Pressing it puts your elytra on (the chestplate goes into the elytra's slot). If you
 * are already wearing the elytra it puts a chestplate back on. With "Re-equip on landing" it stays armed after putting
 * the elytra on and swaps the chestplate back when you touch the ground or water.
 */
public final class ElytraSwap extends Module {
    private final Setting.Bool landing = new Setting.Bool("Re-equip chestplate on landing", true);
    private final Setting.Bool chat = new Setting.Bool("Tell me when something is missing", true);

    private boolean swapped, wasAir;
    private int airTicks;

    public ElytraSwap() {
        super("Elytra Swap", "Press its key to swap chestplate and elytra; can re-equip the chestplate on landing.", Category.COMBAT);
        add(landing, chat);
    }

    @Override
    public void onEnable() {
        swapped = wasAir = false;
        airTicks = 0;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) {
            setEnabled(false);
            return;
        }
        if (p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            int c = findChestplate();
            if (c >= 0) swapChest(c);
            else warn("no chestplate in your inventory");
            setEnabled(false);
            return;
        }
        int e = findElytra();
        if (e < 0) {
            warn("no usable elytra in your inventory");
            setEnabled(false);
            return;
        }
        swapChest(e);
        swapped = true;
        if (!landing.on()) setEnabled(false);
    }

    @Override
    public void onDisable() {
        ClientPlayerEntity p = mc.player;
        if (swapped && p != null && mc.interactionManager != null
                && p.getEquippedStack(EquipmentSlot.CHEST).isOf(Items.ELYTRA)) {
            swapped = false;
            int c = findChestplate();
            if (c >= 0) swapChest(c);
        }
        swapped = false;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || !swapped) return;
        if (!p.isOnGround()) {
            wasAir = true;
            airTicks++;
        }
        boolean down = p.isOnGround() || p.isTouchingWater();
        if (wasAir && airTicks > 5 && down) {
            swapped = false;
            int c = findChestplate();
            if (c >= 0) swapChest(c);
            setEnabled(false);
        }
    }

    private void warn(String msg) {
        if (chat.on() && mc.player != null) {
            mc.player.sendMessage(Text.literal("[NITRO] Elytra Swap: " + msg).formatted(Formatting.RED), false);
        }
    }

    private int findElytra() {
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(Items.ELYTRA) && s.getMaxDamage() - s.getDamage() > 1) return i;
        }
        return -1;
    }

    /** Best chestplate in the inventory: netherite, then diamond, then any other. */
    private int findChestplate() {
        int diamond = -1, other = -1;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isIn(ItemTags.CHEST_ARMOR) || s.isOf(Items.ELYTRA)) continue;
            if (s.isOf(Items.NETHERITE_CHESTPLATE)) return i;
            if (s.isOf(Items.DIAMOND_CHESTPLATE) && diamond < 0) diamond = i;
            if (other < 0) other = i;
        }
        return diamond >= 0 ? diamond : other;
    }

    /** Swaps the worn chest item with the item in an inventory slot (0-8 hotbar, 9-35 inventory). */
    private void swapChest(int invSlot) {
        int screen = invSlot < 9 ? 36 + invSlot : invSlot;
        int sync = mc.player.playerScreenHandler.syncId;
        mc.interactionManager.clickSlot(sync, screen, 0, SlotActionType.PICKUP, mc.player);
        mc.interactionManager.clickSlot(sync, 6, 0, SlotActionType.PICKUP, mc.player); // 6 = chest armor slot
        if (!mc.player.playerScreenHandler.getCursorStack().isEmpty()) {
            mc.interactionManager.clickSlot(sync, screen, 0, SlotActionType.PICKUP, mc.player);
        }
    }
}
