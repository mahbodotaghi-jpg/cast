package dev.nitro.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.ColorUtil;
import dev.nitro.util.RenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/**
 * Aura: a glowing halo above your head, spinning rings around your body, or a pair of flapping wings on your back.
 * Drawn in the world, so you see it in third person (and Freecam); it stays hidden in first person.
 */
public final class Aura extends Module {
    public enum Style {
        HALO("Halo"), RINGS("Rings"), WINGS("Wings");
        private final String label;
        Style(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    public enum Tint {
        CUSTOM("Custom colour"), RAINBOW("Rainbow");
        private final String label;
        Tint(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    private final Setting.Choice<Style> style = new Setting.Choice<>("Style", Style.HALO, Style.class);
    private final Setting.Choice<Tint> tint = new Setting.Choice<>("Colour mode", Tint.CUSTOM, Tint.class);
    private final Setting.Color custom = new Setting.Color("Colour", 0xFFD84D);
    private final Setting.Num size = new Setting.Num("Size", 1.0, 0.5, 2.5, 0.05);
    private final Setting.Num speed = new Setting.Num("Animation speed", 1.0, 0.2, 4.0, 0.1);
    private final Setting.Num lineWidth = new Setting.Num("Line width", 2.0, 1.0, 5.0, 0.5);
    private final Setting.Bool fill = new Setting.Bool("Glow fill", true);
    private final Setting.Num fillOpacity = new Setting.Num("Fill opacity", 30, 5, 80, 5);

    public Aura() {
        super("Aura", "A halo, spinning rings or wings on your character (third person).", Category.RENDER);
        add(style, tint, custom, size, speed, lineWidth, fill, fillOpacity);
        custom.showIf(() -> tint.value == Tint.CUSTOM);
        fillOpacity.showIf(fill::on);
    }

    private int colour(double t, double phase) {
        if (tint.value == Tint.RAINBOW) {
            return ColorUtil.hsv((float) (((t * 0.12 + phase) % 1.0 + 1.0) % 1.0), 0.75f, 1f);
        }
        return custom.rgb();
    }

    @Override
    public void renderWorld(WorldRenderContext ctx) {
        if (mc.player == null || mc.world == null || mc.player.isSpectator()) return;
        MatrixStack ms = ctx.matrixStack();
        Camera camera = ctx.camera();
        if (ms == null || camera == null) return;
        if (!camera.isThirdPerson() && !Freecam.active()) return; // never draw it inside your own head

        float td = mc.getRenderTickCounter().getTickDelta(true);
        Vec3d cam = camera.getPos();
        Vec3d p = mc.player.getLerpedPos(td);
        float bx = (float) (p.x - cam.x), by = (float) (p.y - cam.y), bz = (float) (p.z - cam.z);
        Matrix4f m = ms.peek().getPositionMatrix();
        double t = System.nanoTime() / 1.0e9 * speed.d();
        float h = mc.player.getHeight();

        RenderUtil.begin();
        RenderSystem.enableDepthTest(); // the aura sits in the world instead of showing through walls
        switch (style.value) {
            case HALO -> halo(m, bx, by + h + 0.3f + 0.03f * (float) Math.sin(t * 2.0), bz, t);
            case RINGS -> rings(m, bx, by, bz, h, t);
            case WINGS -> {
                float yaw = MathHelper.lerpAngleDegrees(td, mc.player.prevBodyYaw, mc.player.bodyYaw);
                wings(m, bx, by + h * 0.72f, bz, yaw, t);
            }
        }
        RenderUtil.end();
    }

    // ------------------------------------------------------------ halo

    private void halo(Matrix4f m, float cx, float cy, float cz, double t) {
        int seg = 48;
        float r1 = 0.30f * size.f(), r0 = r1 - 0.07f * size.f();
        int fa = ((int) (fillOpacity.d() * 2.55)) << 24;

        if (fill.on()) {
            BufferBuilder q = RenderUtil.quads();
            for (int i = 0; i < seg; i++) {
                double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
                int c = fa | colour(t, (double) i / seg);
                q.vertex(m, cx + (float) Math.cos(a0) * r0, cy, cz + (float) Math.sin(a0) * r0).color(c);
                q.vertex(m, cx + (float) Math.cos(a0) * r1, cy, cz + (float) Math.sin(a0) * r1).color(c);
                q.vertex(m, cx + (float) Math.cos(a1) * r1, cy, cz + (float) Math.sin(a1) * r1).color(c);
                q.vertex(m, cx + (float) Math.cos(a1) * r0, cy, cz + (float) Math.sin(a1) * r0).color(c);
            }
            RenderUtil.draw(q);
        }

        BufferBuilder b = RenderUtil.lines(lineWidth.f());
        int spark = (int) (t * 10.0);
        for (int i = 0; i < seg; i++) {
            double a0 = Math.PI * 2 * i / seg, a1 = Math.PI * 2 * (i + 1) / seg;
            int base = colour(t, (double) i / seg);
            // a bright arc travels around the ring so you can see it spin
            boolean lit = Math.floorMod(i - spark, seg) < 6;
            int c = 0xFF000000 | (lit ? ColorUtil.lerp(base, 0xFFFFFF, 0.7f) : base);
            for (float r : new float[]{r0, r1}) {
                RenderUtil.line(b, m,
                        cx + (float) Math.cos(a0) * r, cy, cz + (float) Math.sin(a0) * r,
                        cx + (float) Math.cos(a1) * r, cy, cz + (float) Math.sin(a1) * r, c);
            }
        }
        RenderUtil.draw(b);
    }

    // ------------------------------------------------------------ rings

    private void rings(Matrix4f m, float cx, float cy, float cz, float h, double t) {
        BufferBuilder b = RenderUtil.lines(lineWidth.f());
        int seg = 64;
        for (int k = 0; k < 3; k++) {
            float y = cy + h * (0.12f + 0.38f * k) + 0.08f * (float) Math.sin(t * 1.3 + k * 2.1);
            float r = (0.55f + 0.06f * k) * size.f();
            double rot = t * (1.1 + 0.5 * k) * (k % 2 == 0 ? 1 : -1);
            for (int j = 0; j < seg; j++) {
                double frac = (j / (double) seg) * 8.0; // 8 dashes per ring
                if (frac - Math.floor(frac) > 0.55) continue;
                double a0 = Math.PI * 2 * j / seg + rot, a1 = Math.PI * 2 * (j + 1) / seg + rot;
                int c = 0xFF000000 | colour(t, k * 0.2 + (double) j / seg * 0.3);
                RenderUtil.line(b, m,
                        cx + (float) Math.cos(a0) * r, y, cz + (float) Math.sin(a0) * r,
                        cx + (float) Math.cos(a1) * r, y, cz + (float) Math.sin(a1) * r, c);
            }
        }
        RenderUtil.draw(b);
    }

    // ------------------------------------------------------------ wings

    private void wings(Matrix4f m, float rx, float ry, float rz, float yawDeg, double t) {
        double yaw = Math.toRadians(yawDeg);
        float backX = (float) Math.sin(yaw), backZ = (float) -Math.cos(yaw);   // behind the body
        float rightX = (float) -Math.cos(yaw), rightZ = (float) -Math.sin(yaw); // to the player's right
        rx += backX * 0.18f;
        rz += backZ * 0.18f;

        float[] len = {0.85f, 1.05f, 1.2f, 1.15f, 1.0f, 0.8f, 0.55f};
        int n = len.length;
        float s = size.f();
        double flap = Math.sin(t * 3.0) * 0.2;
        float sweep = (float) (0.35 + flap);

        float[][][] tips = new float[2][n][3];
        for (int side = 0; side < 2; side++) {
            float sg = side == 0 ? -1f : 1f;
            for (int k = 0; k < n; k++) {
                double ang = -0.45 + 1.6 * k / (n - 1.0);
                float u = (float) Math.cos(ang) * len[k] * s;
                float v = (float) Math.sin(ang) * len[k] * s;
                tips[side][k][0] = rx + rightX * sg * u + backX * u * sweep;
                tips[side][k][1] = ry + v;
                tips[side][k][2] = rz + rightZ * sg * u + backZ * u * sweep;
            }
        }

        if (fill.on()) {
            int fa = ((int) (fillOpacity.d() * 2.55)) << 24;
            BufferBuilder f = RenderUtil.tris();
            for (int side = 0; side < 2; side++) {
                for (int k = 0; k < n - 1; k++) {
                    int c = fa | colour(t, (double) k / n * 0.5);
                    f.vertex(m, rx, ry, rz).color(c);
                    f.vertex(m, tips[side][k][0], tips[side][k][1], tips[side][k][2]).color(c);
                    f.vertex(m, tips[side][k + 1][0], tips[side][k + 1][1], tips[side][k + 1][2]).color(c);
                }
            }
            RenderUtil.draw(f);
        }

        BufferBuilder b = RenderUtil.lines(lineWidth.f());
        for (int side = 0; side < 2; side++) {
            for (int k = 0; k < n; k++) {
                int c = 0xFF000000 | colour(t, (double) k / n * 0.5);
                float[] a = tips[side][k];
                RenderUtil.line(b, m, rx, ry, rz, a[0], a[1], a[2], c);
                if (k < n - 1) {
                    float[] z = tips[side][k + 1];
                    RenderUtil.line(b, m, a[0], a[1], a[2], z[0], z[1], z[2], c);
                }
            }
        }
        RenderUtil.draw(b);
    }
}
