package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.AttackHooks;
import dev.nitro.util.MaceUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;

/**
 * Stun Slam: the classic axe-then-mace combo. While you fall onto a target that is blocking with a shield, your
 * attack is made with an axe from the hotbar (which disables the shield), and a few ticks later the module hits the
 * same target again with a mace from the hotbar. Your original slot comes back after each hit.
 * <p>
 * It is registered before Shield Breaker so it has priority for the swap, and it marks the follow-up hit itself so
 * the other swap modules leave it alone.
 */
public final class StunSlam extends Module implements AttackHooks.Swapper, AttackHooks.Listener {
    private final Setting.Bool playersOnly = new Setting.Bool("Players only", true);
    private final Setting.Bool facingOnly = new Setting.Bool("Only if the shield faces me", true);
    private final Setting.Num minFall = new Setting.Num("Min fall distance", 1.5, 0.5, 10, 0.5);
    private final Setting.Num delay = new Setting.Num("Delay before mace hit (ticks)", 1, 1, 10, 1);
    private final Setting.Num reach = new Setting.Num("Max distance for the mace hit", 3.5, 2.0, 6.0, 0.1);

    /** The target our axe swap was chosen for (set while the attack is being prepared). */
    private LivingEntity candidate;
    /** The stunned target waiting for the mace hit, and the ticks left before it happens. */
    private LivingEntity stunned;
    private int timer;
    /** True while the follow-up mace attack is running, so wantSlot picks the mace. */
    private boolean slamming;
    private int slamSlot = -1;

    public StunSlam() {
        super("Stun Slam", "Falling onto a blocking player: axe hit to break the shield, then a mace hit right after.", Category.COMBAT);
        add(playersOnly, facingOnly, minFall, delay, reach);
        AttackHooks.register((AttackHooks.Swapper) this);
        AttackHooks.register((AttackHooks.Listener) this);
    }

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        reset();
    }

    @Override
    public void onWorldChanged() {
        reset();
    }

    private void reset() {
        candidate = null;
        stunned = null;
        timer = 0;
        slamming = false;
        slamSlot = -1;
    }

    // ------------------------------------------------------------ axe hit (your own click)

    private boolean falling() {
        return mc.player != null && !mc.player.isOnGround() && mc.player.fallDistance >= minFall.d();
    }

    @Override
    public int wantSlot(LivingEntity t) {
        if (!enabled || mc.player == null) return -1;
        if (slamming) return slamSlot; // the follow-up mace hit
        if (playersOnly.on() && !(t instanceof PlayerEntity)) return -1;
        if (!t.isBlocking() || !falling()) return -1;
        if (facingOnly.on()) {
            Vec3d toMe = mc.player.getPos().subtract(t.getPos()).normalize();
            if (t.getRotationVec(1f).dotProduct(toMe) < 0.1) return -1;
        }
        if (maceSlot(t) < 0) return -1; // nothing to follow up with: leave it to Shield Breaker

        int cur = mc.player.getInventory().selectedSlot;
        int axe = -1;
        if (mc.player.getMainHandStack().isIn(ItemTags.AXES)) {
            axe = cur;
        } else {
            for (int i = 0; i < 9 && axe < 0; i++) {
                if (mc.player.getInventory().getStack(i).isIn(ItemTags.AXES)) axe = i;
            }
        }
        if (axe < 0) return -1;
        candidate = t;
        return axe;
    }

    @Override
    public int backDelay() {
        return 0;
    }

    @Override
    public void onHit(LivingEntity target, double fallBefore, boolean maceInHand) {
        if (!enabled || slamming) return;
        if (candidate == target) {
            stunned = target;
            timer = delay.i();
        }
        candidate = null;
    }

    // ------------------------------------------------------------ mace hit (automatic)

    private int maceSlot(LivingEntity t) {
        boolean smart = BreachSwap.INSTANCE != null && BreachSwap.INSTANCE.enabled;
        int minArmor = smart ? BreachSwap.INSTANCE.minArmor() : 1;
        return MaceUtil.find(t, smart, minArmor);
    }

    @Override
    public void onTick() {
        if (stunned == null) return;
        if (mc.player == null || mc.world == null || mc.interactionManager == null || mc.currentScreen != null) {
            stunned = null;
            return;
        }
        if (--timer > 0) return;

        LivingEntity t = stunned;
        stunned = null;
        if (!t.isAlive() || t.isRemoved()) return;
        if (mc.player.distanceTo(t) > reach.d()) return;
        if (!falling()) return;
        int slot = maceSlot(t);
        if (slot < 0) return;

        slamming = true;
        slamSlot = slot;
        try {
            // AttackHooks swaps to the mace right before the packet and restores your slot right after
            mc.interactionManager.attackEntity(mc.player, t);
            mc.player.swingHand(Hand.MAIN_HAND);
        } finally {
            slamming = false;
            slamSlot = -1;
        }
    }
}
