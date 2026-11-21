package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.AttackHooks;
import dev.nitro.util.CombatUtil;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Fall Reset: after a smash hit (a mace hit while you were falling), it cancels the fall that is still ahead of you.
 * Wind charge mode throws a charge at your feet just before you reach the ground. Water bucket mode places water under
 * you just before landing and scoops it up again once you are down.
 */
public final class FallReset extends Module implements AttackHooks.Listener {
    public enum Mode {
        WIND_CHARGE("Wind charge"), WATER_BUCKET("Water bucket");
        private final String label;
        Mode(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    private enum Stage { IDLE, WAIT, WATER_PLACED }

    private final Setting.Choice<Mode> mode = new Setting.Choice<>("Mode", Mode.WIND_CHARGE, Mode.class);
    private final Setting.Num minFall = new Setting.Num("Smash fall distance", 1.5, 1.0, 10, 0.5);
    private final Setting.Num delay = new Setting.Num("Delay after the hit (ticks)", 1, 0, 10, 1);
    private final Setting.Num windHeight = new Setting.Num("Throw when this close to the ground", 2.0, 1.0, 5.0, 0.5);
    private final Setting.Bool switchBack = new Setting.Bool("Switch back after use", true);

    private Stage stage = Stage.IDLE;
    private int wait, timeout;

    public FallReset() {
        super("Fall Reset", "After a smash hit, cancels the rest of your fall with a wind charge or a water bucket.", Category.COMBAT);
        add(mode, minFall, delay, windHeight, switchBack);
        windHeight.showIf(() -> mode.value == Mode.WIND_CHARGE);
        AttackHooks.register(this);
    }

    @Override
    public void onEnable() {
        stage = Stage.IDLE;
    }

    @Override
    public void onWorldChanged() {
        stage = Stage.IDLE;
    }

    @Override
    public void onHit(LivingEntity target, double fallBefore, boolean maceInHand) {
        if (!enabled || !maceInHand || fallBefore < minFall.d()) return;
        stage = Stage.WAIT;
        wait = delay.i();
        timeout = 100;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || stage == Stage.IDLE) return;
        if (--timeout <= 0) {
            stage = Stage.IDLE;
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }

        if (stage == Stage.WAIT) {
            if (p.isOnGround()) { // landed without needing the reset
                stage = Stage.IDLE;
                return;
            }
            boolean falling = p.getVelocity().y < -0.05;
            double ground = CombatUtil.groundDistance();
            if (mode.value == Mode.WIND_CHARGE) {
                if (falling && ground <= windHeight.d()) {
                    int slot = CombatUtil.findHotbar(Items.WIND_CHARGE);
                    if (slot < 0) {
                        warn("no wind charge in the hotbar");
                    } else {
                        CombatUtil.useSilently(slot, p.getYaw(), 90f, switchBack.on());
                    }
                    stage = Stage.IDLE;
                }
            } else if (falling && ground <= 3.5 && p.fallDistance > 3.0) {
                int slot = CombatUtil.findHotbar(Items.WATER_BUCKET);
                if (slot < 0) {
                    warn("no water bucket in the hotbar");
                    stage = Stage.IDLE;
                } else if (CombatUtil.useSilently(slot, p.getYaw(), 90f, switchBack.on())) {
                    stage = Stage.WATER_PLACED;
                    wait = 1;
                }
            }
        } else if (stage == Stage.WATER_PLACED) {
            if (p.isOnGround() || p.isTouchingWater()) {
                int bucket = CombatUtil.findHotbar(Items.BUCKET);
                if (bucket >= 0) CombatUtil.useSilently(bucket, p.getYaw(), 90f, switchBack.on());
                stage = Stage.IDLE;
            }
        }
    }

    private void warn(String msg) {
        if (mc.player != null) {
            mc.player.sendMessage(Text.literal("[NITRO] Fall Reset: " + msg).formatted(Formatting.RED), false);
        }
    }
}
