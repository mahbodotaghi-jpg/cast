package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.AttackHooks;
import dev.nitro.util.MaceUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

/**
 * Breach Swap: while you hold a mace, an attack on an armored target uses your Breach mace and an attack on an
 * unarmored target uses your Density mace (any mace if the preferred one is missing). Also tells Mace Swap which mace
 * to pick.
 */
public final class BreachSwap extends Module implements AttackHooks.Swapper {
    public static BreachSwap INSTANCE;

    private final Setting.Num minArmor = new Setting.Num("Armor points to count as armored", 1, 1, 20, 1);
    private final Setting.Bool playersOnly = new Setting.Bool("Players only", true);
    private final Setting.Num backDelay = new Setting.Num("Switch back delay (ticks)", 0, 0, 10, 1);

    public BreachSwap() {
        super("Breach Swap", "Attacks armored targets with a Breach mace and unarmored ones with a Density mace.", Category.COMBAT);
        add(minArmor, playersOnly, backDelay);
        INSTANCE = this;
        AttackHooks.register(this);
    }

    public int minArmor() {
        return minArmor.i();
    }

    @Override
    public int wantSlot(LivingEntity t) {
        if (!enabled || mc.player == null) return -1;
        if (playersOnly.on() && !(t instanceof PlayerEntity)) return -1;
        if (!mc.player.getMainHandStack().isOf(Items.MACE)) return -1; // Mace Swap handles "not holding a mace"
        int best = MaceUtil.find(t, true, minArmor.i());
        if (best < 0 || best == mc.player.getInventory().selectedSlot) return -1;
        return best;
    }

    @Override
    public int backDelay() {
        return backDelay.i();
    }
}
