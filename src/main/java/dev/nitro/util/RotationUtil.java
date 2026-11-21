package dev.nitro.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.ThreadLocalRandom;

/** Aim helpers. Turns are quantized to the smallest step your mouse sensitivity can produce, like real mouse input. */
public final class RotationUtil {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private RotationUtil() {}

    /** {yaw, pitch} that looks from {@code from} to {@code to}. */
    public static float[] angles(Vec3d from, Vec3d to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, h));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -90f, 90f)};
    }

    /** Smallest angle (degrees) a mouse movement can change the view by at the current sensitivity. */
    public static double gcd() {
        double s = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        return s * s * s * 8.0 * 0.15;
    }

    public static float quantize(float delta) {
        double g = gcd();
        if (g <= 0) return delta;
        return (float) (Math.round(delta / g) * g);
    }

    /**
     * Turns the player toward the target angles. {@code snap} jumps straight there, otherwise each axis moves at most
     * {@code maxStep} degrees this tick (with a little natural variation). Returns true when the aim is within 1.5 degrees.
     */
    public static boolean turn(ClientPlayerEntity p, float targetYaw, float targetPitch, float maxStep, boolean snap) {
        float dy = MathHelper.wrapDegrees(targetYaw - p.getYaw());
        float dp = targetPitch - p.getPitch();
        float sy = dy, sp = dp;
        if (!snap) {
            float k = 0.85f + ThreadLocalRandom.current().nextFloat() * 0.3f;
            sy = MathHelper.clamp(dy, -maxStep, maxStep) * k;
            sp = MathHelper.clamp(dp, -maxStep, maxStep) * k;
            // never overshoot
            if (Math.abs(sy) > Math.abs(dy)) sy = dy;
            if (Math.abs(sp) > Math.abs(dp)) sp = dp;
        }
        sy = quantize(sy);
        sp = quantize(sp);
        if (sy != 0f || sp != 0f) {
            p.setYaw(p.getYaw() + sy);
            p.setPitch(MathHelper.clamp(p.getPitch() + sp, -90f, 90f));
        }
        return Math.abs(dy) < 1.5f && Math.abs(dp) < 1.5f;
    }
}
