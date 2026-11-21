package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.AttackHooks;
import dev.nitro.util.MaceUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

/**
 * Breach Swap: an attack on an armored target uses your Breach mace and an attack on an unarmored target uses your
 * Density mace (any mace if the preferred one is missing). Works while you already hold a mace, and - with "Swap from
 * other items" on - also when you hold a sword or anything else (while falling, like Mace Swap). Also tells Mace Swap
 * which mace to pick.
 */
public final class BreachSwap extends Module implements AttackHooks.Swapper {
    public static BreachSwap INSTANCE;

    private final Setting.Num minArmor = new Setting.Num("Armor points to count as armored", 1, 1, 20, 1);
    private final Setting.Bool playersOnly = new Setting.Bool("Players only", true);
    private final Setting.Bool fromOther = new Setting.Bool("Swap from other items", true);
    private final Setting.Bool fallOnly = new Setting.Bool("Only while falling", true);
    private final Setting.Num minFall = new Setting.Num("Min fall distance", 1.5, 0.5, 10, 0.5);
    private final Setting.Num backDelay = new Setting.Num("Switch back delay (ticks)", 0, 0, 10, 1);

    public BreachSwap() {
        super("Breach Swap", "Attacks armored targets with a Breach mace and unarmored ones with a Density mace.", Category.COMBAT);
        add(minArmor, playersOnly, fromOther, fallOnly, minFall, backDelay);
        fallOnly.showIf(fromOther::on);
        minFall.showIf(() -> fromOther.on() && fallOnly.on());
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
        if (!mc.player.getMainHandStack().isOf(Items.MACE)) {
            if (!fromOther.on()) return -1;
            if (fallOnly.on() && (mc.player.isOnGround() || mc.player.fallDistance < minFall.d())) return -1;
        }
        int best = MaceUtil.find(t, true, minArmor.i());
        if (best < 0 || best == mc.player.getInventory().selectedSlot) return -1;
        return best;
    }

    @Override
    public int backDelay() {
        return backDelay.i();
    }
}
