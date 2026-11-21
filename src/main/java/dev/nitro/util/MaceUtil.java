package dev.nitro.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;

/** Finds the right mace in the hotbar. */
public final class MaceUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private MaceUtil() {}

    public static boolean has(ItemStack s, RegistryKey<Enchantment> key) {
        for (RegistryEntry<Enchantment> e : s.getEnchantments().getEnchantments()) {
            if (e.matchesKey(key)) return true;
        }
        return false;
    }

    /**
     * Hotbar slot of a mace, or -1. With {@code smart} a Breach mace is preferred against armored targets and a
     * Density mace against unarmored ones; if the preferred kind is missing any mace is used.
     */
    public static int find(LivingEntity target, boolean smart, int minArmor) {
        if (mc.player == null) return -1;
        int plain = -1, breach = -1, density = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (!s.isOf(Items.MACE)) continue;
            if (plain < 0) plain = i;
            if (breach < 0 && has(s, Enchantments.BREACH)) breach = i;
            if (density < 0 && has(s, Enchantments.DENSITY)) density = i;
        }
        if (!smart || plain < 0) return plain;
        boolean armored = target.getArmor() >= minArmor;
        int pick = armored ? breach : density;
        return pick >= 0 ? pick : plain;
    }
}
