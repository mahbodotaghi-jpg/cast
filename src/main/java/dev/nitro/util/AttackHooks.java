package dev.nitro.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs right before and after every client attack (see ClientPlayerInteractionManagerMixin). Swap modules (Shield
 * Breaker, Mace Swap, Breach Swap) register as {@link Swapper}s; the first one that wants a slot wins, the slot is
 * selected before the attack packet goes out, and your original slot is restored afterwards.
 */
public final class AttackHooks {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    public interface Swapper {
        /** Hotbar slot (0-8) to attack with, or -1 for no opinion. Must return -1 while the module is off. */
        int wantSlot(LivingEntity target);

        /** Ticks to wait before switching back (0 = same tick, right after the attack). */
        int backDelay();
    }

    public interface Listener {
        /** Called after an attack. {@code fallBefore} is your fall distance when the attack started. */
        void onHit(LivingEntity target, double fallBefore, boolean maceInHand);
    }

    private static final List<Swapper> swappers = new ArrayList<>();
    private static final List<Listener> listeners = new ArrayList<>();
    private static int backSlot = -1, backTimer, backDelay;
    private static double fallBefore;

    private AttackHooks() {}

    public static void register(Swapper s) {
        swappers.add(s);
    }

    public static void register(Listener l) {
        listeners.add(l);
    }

    public static void pre(Entity target) {
        if (mc.player == null || !(target instanceof LivingEntity t)) return;
        fallBefore = mc.player.fallDistance;
        int cur = mc.player.getInventory().selectedSlot;
        for (Swapper s : swappers) {
            int want = s.wantSlot(t);
            if (want < 0 || want > 8) continue;
            if (want != cur) {
                if (backSlot < 0) backSlot = cur;
                backDelay = s.backDelay();
                backTimer = 0;
                // the vanilla slot sync sends the slot packet right before the attack packet
                mc.player.getInventory().selectedSlot = want;
            }
            break;
        }
    }

    public static void post(Entity target) {
        if (mc.player == null || !(target instanceof LivingEntity t)) return;
        boolean mace = mc.player.getMainHandStack().isOf(Items.MACE);
        for (Listener l : listeners) l.onHit(t, fallBefore, mace);
        if (backSlot >= 0) {
            if (backDelay <= 0) {
                CombatUtil.selectHotbar(backSlot);
                backSlot = -1;
            } else {
                backTimer = backDelay;
            }
        }
    }

    public static void tick() {
        if (backSlot >= 0 && backTimer > 0 && --backTimer == 0) {
            CombatUtil.selectHotbar(backSlot);
            backSlot = -1;
        }
    }

    public static void reset() {
        backSlot = -1;
        backTimer = 0;
    }
}
