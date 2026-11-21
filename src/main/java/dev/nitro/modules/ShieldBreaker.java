package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.AttackHooks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Vec3d;

/**
 * Shield Breaker: when you attack a target that is blocking with a shield, the hit is made with an axe from your
 * hotbar, then your previous slot comes back.
 */
public final class ShieldBreaker extends Module implements AttackHooks.Swapper {
    private final Setting.Bool playersOnly = new Setting.Bool("Players only", true);
    private final Setting.Bool facingOnly = new Setting.Bool("Only if the shield faces me", true);
    private final Setting.Num backDelay = new Setting.Num("Switch back delay (ticks)", 0, 0, 10, 1);

    public ShieldBreaker() {
        super("Shield Breaker", "Hits a blocking target with an axe from your hotbar, then switches back.", Category.COMBAT);
        add(playersOnly, facingOnly, backDelay);
        AttackHooks.register(this);
    }

    @Override
    public int wantSlot(LivingEntity t) {
        if (!enabled || mc.player == null) return -1;
        if (playersOnly.on() && !(t instanceof PlayerEntity)) return -1;
        if (!t.isBlocking()) return -1;
        if (facingOnly.on()) {
            Vec3d toMe = mc.player.getPos().subtract(t.getPos()).normalize();
            if (t.getRotationVec(1f).dotProduct(toMe) < 0.1) return -1;
        }
        if (mc.player.getMainHandStack().isIn(ItemTags.AXES)) return -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isIn(ItemTags.AXES)) return i;
        }
        return -1;
    }

    @Override
    public int backDelay() {
        return backDelay.i();
    }
}
