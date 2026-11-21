package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.AttackHooks;
import dev.nitro.util.MaceUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;

/**
 * Mace Swap: the hit is made with a mace from your hotbar while you fall, then your sword slot comes back right
 * after. The swap happens inside the attack, so the server sees mace and attack in the same tick.
 */
public final class MaceSwap extends Module implements AttackHooks.Swapper {
    public static MaceSwap INSTANCE;

    private final Setting.Bool fallOnly = new Setting.Bool("Only while falling", true);
    private final Setting.Num minFall = new Setting.Num("Min fall distance", 1.5, 0.5, 10, 0.5);
    private final Setting.Bool playersOnly = new Setting.Bool("Players only", true);
    private final Setting.Num backDelay = new Setting.Num("Switch back delay (ticks)", 0, 0, 10, 1);

    public MaceSwap() {
        super("Mace Swap", "Hits with a mace from your hotbar while falling, then switches back to your sword.", Category.COMBAT);
        add(fallOnly, minFall, playersOnly, backDelay);
        minFall.showIf(fallOnly::on);
        INSTANCE = this;
        AttackHooks.register(this);
    }

    @Override
    public int wantSlot(LivingEntity t) {
        if (!enabled || mc.player == null) return -1;
        if (playersOnly.on() && !(t instanceof PlayerEntity)) return -1;
        // already holding a mace: Breach Swap (if on) decides which one
        if (mc.player.getMainHandStack().isOf(Items.MACE)) return -1;
        if (fallOnly.on() && (mc.player.isOnGround() || mc.player.fallDistance < minFall.d())) return -1;
        boolean smart = BreachSwap.INSTANCE != null && BreachSwap.INSTANCE.enabled;
        int minArmor = smart ? BreachSwap.INSTANCE.minArmor() : 1;
        return MaceUtil.find(t, smart, minArmor);
    }

    @Override
    public int backDelay() {
        return backDelay.i();
    }
}
