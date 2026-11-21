package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.RotationUtil;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.math.Vec3d;

/**
 * Smash Aim: while you fall (holding a mace), your aim snaps or smoothly turns onto the nearest player below you.
 * Turns are quantized to your mouse sensitivity.
 */
public final class SmashAim extends Module {
    public enum Mode {
        SNAP("Snap"), SMOOTH("Smooth");
        private final String label;
        Mode(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    public enum Point {
        BODY("Body"), HEAD("Head");
        private final String label;
        Point(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    private final Setting.Choice<Mode> mode = new Setting.Choice<>("Mode", Mode.SMOOTH, Mode.class);
    private final Setting.Num speed = new Setting.Num("Smooth speed (deg per tick)", 25, 5, 90, 1);
    private final Setting.Choice<Point> point = new Setting.Choice<>("Aim point", Point.BODY, Point.class);
    private final Setting.Num range = new Setting.Num("Range", 7, 3, 14, 1);
    private final Setting.Num minFall = new Setting.Num("Start at fall distance", 1.0, 0.0, 10, 0.5);
    private final Setting.Bool maceOnly = new Setting.Bool("Only while holding a mace", true);

    public SmashAim() {
        super("Smash Aim", "While falling with a mace, aims at the nearest player below you.", Category.COMBAT);
        add(mode, speed, point, range, minFall, maceOnly);
        speed.showIf(() -> mode.value == Mode.SMOOTH);
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.currentScreen != null) return;
        if (maceOnly.on() && !p.getMainHandStack().isOf(Items.MACE)) return;
        if (p.isOnGround() || p.getVelocity().y > -0.03 || p.fallDistance < minFall.d()) return;

        AbstractClientPlayerEntity best = null;
        double bestD = range.d();
        for (AbstractClientPlayerEntity t : mc.world.getPlayers()) {
            if (t == p || !t.isAlive() || t.isSpectator()) continue;
            if (t.getY() > p.getY() - 0.3) continue; // only players below
            double d = p.distanceTo(t);
            if (d <= bestD) {
                bestD = d;
                best = t;
            }
        }
        if (best == null) return;

        Vec3d aim = point.value == Point.HEAD
                ? best.getEyePos().add(0, -0.1, 0)
                : best.getPos().add(0, best.getHeight() * 0.5, 0);
        float[] a = RotationUtil.angles(p.getEyePos(), aim);
        RotationUtil.turn(p, a[0], a[1], speed.f(), mode.value == Mode.SNAP);
    }
}
