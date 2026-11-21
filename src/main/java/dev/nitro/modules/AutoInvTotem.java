package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Auto Inv Totem: refills a totem into your offhand and into a chosen hotbar slot from the totems in your inventory.
 * With "Only after a pop" it waits until a totem was consumed (a totem vanished from the offhand or the hotbar slot
 * while no inventory screen was open) and then refills during the pop window.
 */
public final class AutoInvTotem extends Module {
    private final Setting.Bool offhand = new Setting.Bool("Refill offhand", true);
    private final Setting.Bool hotbar = new Setting.Bool("Refill hotbar slot", true);
    private final Setting.Num hotbarSlot = new Setting.Num("Hotbar slot (1-9)", 2, 1, 9, 1);
    private final Setting.Bool replaceItem = new Setting.Bool("Replace another item in that slot", false);
    private final Setting.Bool afterPop = new Setting.Bool("Only after a pop", false);
    private final Setting.Num popWindow = new Setting.Num("Pop window (seconds)", 5, 1, 20, 1);
    private final Setting.Num delay = new Setting.Num("Delay between moves (ticks)", 1, 0, 10, 1);
    private final Setting.Bool humanize = new Setting.Bool("Humanize timing", true);
    private final Setting.Bool invOnly = new Setting.Bool("Only while the inventory is open", false);

    private boolean hadOff, hadHot;
    private int armed, cooldown;

    public AutoInvTotem() {
        super("Auto Inv Totem", "Refills totems into your offhand and a hotbar slot from your inventory.", Category.COMBAT);
        add(offhand, hotbar, hotbarSlot, replaceItem, afterPop, popWindow, delay, humanize, invOnly);
        hotbarSlot.showIf(hotbar::on);
        replaceItem.showIf(hotbar::on);
        popWindow.showIf(afterPop::on);
    }

    @Override
    public void onEnable() {
        armed = cooldown = 0;
        hadOff = hadHot = false;
    }

    @Override
    public void onWorldChanged() {
        armed = cooldown = 0;
        hadOff = hadHot = false;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.interactionManager == null) return;

        int hs = hotbarSlot.i() - 1;
        boolean offNow = p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
        boolean hotNow = p.getInventory().getStack(hs).isOf(Items.TOTEM_OF_UNDYING);
        boolean lost = (hadOff && !offNow && offhand.on()) || (hadHot && !hotNow && hotbar.on());
        hadOff = offNow;
        hadHot = hotNow;
        if (lost && mc.currentScreen == null && p.isAlive()) armed = popWindow.i() * 20;
        if (armed > 0) armed--;

        if (afterPop.on() && armed <= 0) return;
        if (invOnly.on() && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (mc.currentScreen != null && !(mc.currentScreen instanceof InventoryScreen)) return;
        if (cooldown > 0) {
            cooldown--;
            return;
        }

        int sync = p.playerScreenHandler.syncId;
        if (offhand.on() && !offNow) {
            int src = findTotem(hotbar.on() ? hs : -1);
            if (src >= 0) {
                // button 40 = swap the clicked slot with the offhand
                mc.interactionManager.clickSlot(sync, screenSlot(src), 40, SlotActionType.SWAP, p);
                nextMove();
                return;
            }
        }
        if (hotbar.on() && !hotNow) {
            ItemStack cur = p.getInventory().getStack(hs);
            if (cur.isEmpty() || replaceItem.on()) {
                int src = findMainInventoryTotem();
                if (src >= 0) {
                    mc.interactionManager.clickSlot(sync, src, hs, SlotActionType.SWAP, p);
                    nextMove();
                    return;
                }
            }
        }
        if ((!offhand.on() || offNow) && (!hotbar.on() || hotNow)) armed = 0; // everything is refilled
    }

    private void nextMove() {
        cooldown = delay.i() + (humanize.on() ? ThreadLocalRandom.current().nextInt(0, 2) : 0);
    }

    private static int screenSlot(int inv) {
        return inv < 9 ? 36 + inv : inv;
    }

    /** Totem in the main inventory (9-35), then the hotbar except {@code keep}. Returns an inventory index or -1. */
    private int findTotem(int keep) {
        int m = findMainInventoryTotem();
        if (m >= 0) return m;
        for (int i = 0; i < 9; i++) {
            if (i != keep && mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }

    private int findMainInventoryTotem() {
        for (int i = 9; i < 36; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        return -1;
    }
}
