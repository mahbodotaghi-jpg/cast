package dev.nitro.modules;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.ColorUtil;
import dev.nitro.util.RenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.World;
import org.joml.Matrix4f;

import java.util.Random;

/**
 * Custom Sky: replaces the sky with one of three animated scenes (Violet Galaxy, Thunderstorm, Violet Marble).
 * Drawn right after the terrain, with depth testing but no depth writing, so it only fills pixels that nothing
 * solid has covered. Clouds, weather, particles and translucent blocks are drawn afterwards and stay on top.
 * Purely visual: the real world time is never touched.
 */
public final class CustomSky extends Module {
    public enum Mode {
        GALAXY("Violet Galaxy"), STORM("Thunderstorm"), MARBLE("Violet Marble");
        private final String label;
        Mode(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    public enum Stage {
        AFTER_TERRAIN("After terrain (clouds stay on top)"), END_OF_FRAME("End of frame (fallback)");
        private final String label;
        Stage(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    private static final int RINGS = 64, SEGS = 128, MAX_STARS = 1500;
    private static final double TAU = Math.PI * 2.0;

    private final Setting.Choice<Mode> mode = new Setting.Choice<>("Sky", Mode.GALAXY, Mode.class);
    private final Setting.Num speed = new Setting.Num("Animation speed", 1.0, 0.1, 5.0, 0.1);
    private final Setting.Bool stars = new Setting.Bool("Stars", true);
    private final Setting.Num starCount = new Setting.Num("Star count", 700, 100, 1500, 100);
    private final Setting.Bool planet = new Setting.Bool("Ringed planet", true);
    private final Setting.Bool lightning = new Setting.Bool("Lightning", true);
    private final Setting.Bool overworldOnly = new Setting.Bool("Overworld only", true);
    private final Setting.Choice<Stage> stage = new Setting.Choice<>("Draw stage", Stage.AFTER_TERRAIN, Stage.class);

    private boolean drewEarly;
    private String lastDiag = "";

    private final float[] starX = new float[MAX_STARS], starY = new float[MAX_STARS], starZ = new float[MAX_STARS];
    private final double[] bas = new double[6];
    private final int[] col = new int[(RINGS + 1) * (SEGS + 1)];

    // current lightning bolt (regenerated for every flash)
    private int boltIndex = Integer.MIN_VALUE;
    private final double[] boltAz = new double[16], boltEl = new double[16];
    private double boltBaseAz;

    private static final double[] SIN_LAT = new double[RINGS + 1], COS_LAT = new double[RINGS + 1];
    private static final double[] SIN_AZ = new double[SEGS + 1], COS_AZ = new double[SEGS + 1];

    static {
        for (int i = 0; i <= RINGS; i++) {
            double lat = -Math.PI / 2.0 + Math.PI * i / RINGS;
            SIN_LAT[i] = Math.sin(lat);
            COS_LAT[i] = Math.cos(lat);
        }
        for (int j = 0; j <= SEGS; j++) {
            double az = TAU * j / SEGS;
            SIN_AZ[j] = Math.sin(az);
            COS_AZ[j] = Math.cos(az);
        }
    }

    public CustomSky() {
        super("Custom Sky", "Replaces the sky with a violet galaxy, a thunderstorm or a violet marble swirl.", Category.RENDER);
        add(mode, speed, stars, starCount, planet, lightning, overworldOnly, stage);
        stars.showIf(() -> mode.value == Mode.GALAXY);
        starCount.showIf(() -> mode.value == Mode.GALAXY && stars.on());
        planet.showIf(() -> mode.value == Mode.GALAXY);
        lightning.showIf(() -> mode.value == Mode.STORM);

        Random r = new Random(1337L);
        for (int i = 0; i < MAX_STARS; i++) {
            double y = r.nextDouble() * 2.0 - 1.0;
            double az = r.nextDouble() * TAU;
            double rr = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            starX[i] = (float) (rr * Math.cos(az));
            starY[i] = (float) y;
            starZ[i] = (float) (rr * Math.sin(az));
        }
    }

    // ---- noise ------------------------------------------------------------------------------------------------

    private static float hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float fade(float t) {
        return t * t * (3f - 2f * t);
    }

    private static float lerpf(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float noise(float x, float y, float z) {
        int xi = (int) Math.floor(x), yi = (int) Math.floor(y), zi = (int) Math.floor(z);
        float fx = fade(x - xi), fy = fade(y - yi), fz = fade(z - zi);
        float c000 = hash(xi, yi, zi), c100 = hash(xi + 1, yi, zi), c010 = hash(xi, yi + 1, zi), c110 = hash(xi + 1, yi + 1, zi);
        float c001 = hash(xi, yi, zi + 1), c101 = hash(xi + 1, yi, zi + 1), c011 = hash(xi, yi + 1, zi + 1), c111 = hash(xi + 1, yi + 1, zi + 1);
        float x00 = lerpf(c000, c100, fx), x10 = lerpf(c010, c110, fx), x01 = lerpf(c001, c101, fx), x11 = lerpf(c011, c111, fx);
        return lerpf(lerpf(x00, x10, fy), lerpf(x01, x11, fy), fz);
    }

    private static float fbm(float x, float y, float z, int oct) {
        float sum = 0f, amp = 0.5f, norm = 0f;
        for (int i = 0; i < oct; i++) {
            sum += noise(x, y, z) * amp;
            norm += amp;
            x *= 2.03f;
            y *= 2.03f;
            z *= 2.03f;
            amp *= 0.5f;
        }
        return sum / norm;
    }

    private static float smooth(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }

    // ---- sky colours ------------------------------------------------------------------------------------------

    /** Violet galaxy: deep indigo top, lilac glow at the horizon, vertical aurora curtains. */
    private static int galaxy(double az, double el, float t) {
        int c;
        if (el >= 0) c = ColorUtil.lerp(0xA566F0, 0x14072E, (float) Math.pow(el, 0.45));
        else c = ColorUtil.lerp(0xA566F0, 0x1B0B3D, (float) Math.pow(-el, 0.4));

        float e = (float) el;
        double wave = 0.5 + 0.5 * Math.sin(az * 2.3 + t * 0.25 + Math.sin(az * 5.0 - t * 0.2) * 1.2);
        double rays = 0.5 + 0.5 * Math.sin(az * 46.0 + Math.sin(az * 5.0 + t * 0.4) * 3.0);
        double env = smooth(0.0f, 0.18f, e) * Math.exp(-Math.pow((e - 0.30) / 0.30, 2));
        float k = (float) (env * wave * (0.35 + 0.65 * rays));
        int aur = ColorUtil.lerp(0xE879F9, 0x9F67FF, smooth(0.05f, 0.55f, e));
        c = ColorUtil.lerp(c, aur, Math.min(0.90f, k * 1.05f));

        // Layered nebula clouds and a subtle spiral-galaxy sweep.
        double spiral = az * 3.2 + e * 10.0 - t * 0.10;
        double arm = Math.exp(-Math.pow(Math.sin(spiral) * 3.0, 2))
                * Math.exp(-Math.pow((e - 0.24) / 0.42, 2));
        double cloud = fbm((float)(Math.cos(az) * 2.8 + t * 0.012),
                (float)(e * 3.4), (float)(Math.sin(az) * 2.8 - t * 0.009), 4);
        float nebula = (float)(arm * (0.20 + cloud * 0.55));
        c = ColorUtil.lerp(c, 0x45D9FF, Math.min(0.48f, nebula * 0.48f));
        c = ColorUtil.lerp(c, 0xFF69D4, Math.min(0.42f, (float)(env * cloud * 0.55)));
        double dust = Math.pow(Math.max(0.0, Math.sin(az * 17.0 + e * 29.0 + t * 0.08)), 18);
        c = ColorUtil.lerp(c, 0xFFF0FF, (float)(dust * env * 0.22));

        // bright line right on the horizon
        float line = (float) Math.exp(-Math.pow(el / 0.018, 2));
        c = ColorUtil.lerp(c, 0xF0DDFF, Math.min(1f, line * 0.9f));
        return c;
    }

    /** Dark storm clouds with a teal tint, brightened by lightning. */
    private static int storm(double dx, double dy, double dz, double el, float t, float flash, double flashAz, double az) {
        float cx = (float) dx * 2.0f + t * 0.020f, cy = (float) dy * 2.6f, cz = (float) dz * 2.0f - t * 0.014f;
        float n = fbm(cx, cy, cz, 5) * 0.7f + fbm(cx * 2.6f + 7.1f, cy * 2.6f, cz * 2.6f - t * 0.03f, 3) * 0.3f;
        float lit = smooth(0.38f, 0.78f, n);
        float band = 0.35f + 0.65f * (float) Math.exp(-Math.pow((el - 0.22) / 0.45, 2));
        int c = ColorUtil.lerp(0x090E10, 0x52666B, lit * band);
        c = ColorUtil.lerp(c, 0x0A2A2C, smooth(-0.05f, 0.5f, (float) el) * 0.25f);
        if (el < 0) c = ColorUtil.lerp(c, 0x070B0C, Math.min(1f, (float) -el * 3f));
        if (flash > 0.01f) {
            double d = Math.abs(az - flashAz);
            d = Math.min(d, TAU - d);
            float near = (float) Math.exp(-d * d / 0.35) * (0.35f + 0.65f * lit);
            c = ColorUtil.lerp(c, 0xBFF7F0, Math.min(1f, flash * near * 0.85f + flash * 0.08f));
        }
        return c;
    }

    /** Dark violet liquid marble: domain-warped noise with thin bright veins. */
    private static int marble(double dx, double dy, double dz, double el, float t) {
        float px = (float) dx * 2.2f, py = (float) dy * 2.2f, pz = (float) dz * 2.2f;
        float wx = fbm(px + t * 0.03f, py, pz + 3.1f, 3) - 0.5f;
        float wy = fbm(px + 8.2f, py + t * 0.025f, pz, 3) - 0.5f;
        float wz = fbm(px, py + 5.7f, pz - t * 0.03f, 3) - 0.5f;
        float n = fbm(px + wx * 2.6f, py + wy * 2.6f, pz + wz * 2.6f, 4);
        float vein = 1f - Math.abs(2f * n - 1f);
        vein = vein * vein * vein * vein;
        int c = ColorUtil.lerp(0x06030E, 0x2E1760, smooth(0.25f, 0.8f, n));
        c = ColorUtil.lerp(c, 0x9C6FE8, Math.min(1f, vein * 0.75f));
        // purple haze rising from the horizon
        float haze = (1f - smooth(-0.1f, 0.35f, (float) el)) * 0.45f;
        c = ColorUtil.lerp(c, 0x6B3FC0, haze);
        return c;
    }

    // ---- rendering --------------------------------------------------------------------------------------------

    private void diag(String msg) {
        if (msg.equals(lastDiag)) return;
        lastDiag = msg;
        System.out.println("[NITRO] Custom Sky: " + msg);
    }

    /** Right after the terrain (default). */
    @Override
    public void renderSky(WorldRenderContext ctx) {
        drewEarly = false;
        if (stage.value != Stage.AFTER_TERRAIN) return;
        drewEarly = draw(ctx, "after terrain");
    }

    /** End of the frame: fallback when the early event did not draw, or when chosen in the settings. */
    @Override
    public void renderSkyLate(WorldRenderContext ctx) {
        boolean need = stage.value == Stage.END_OF_FRAME || !drewEarly;
        drewEarly = false;
        if (need) draw(ctx, "end of frame");
    }

    private boolean draw(WorldRenderContext ctx, String where) {
        ClientWorld w = mc.world;
        if (w == null || mc.player == null) return false;
        if (overworldOnly.on() && w.getRegistryKey() != World.OVERWORLD) {
            diag("skipped: not in the Overworld (turn off 'Overworld only' to use it elsewhere)");
            return false;
        }
        // No custom sky while under water, in lava or in powder snow.
        if (!ctx.camera().getSubmersionType().name().equals("NONE")) {
            diag("skipped: camera is submerged");
            return false;
        }
        MatrixStack ms = ctx.matrixStack();
        Matrix4f m = ms != null ? ms.peek().getPositionMatrix() : new Matrix4f();

        float t = (float) ((System.nanoTime() / 1_000_000L) % 3_600_000L) / 1000f * speed.f();

        int vd = Math.max(2, mc.options.getViewDistance().getValue());
        float radius = vd * 32f;

        // lightning state (storm only)
        float flash = 0f;
        if (mode.value == Mode.STORM && lightning.on()) {
            float period = 5.5f;
            int n = (int) Math.floor(t / period);
            float ph = t - n * period;
            if (n != boltIndex) makeBolt(n);
            if (ph < 1.0f) {
                flash = (float) Math.min(1.0, Math.exp(-ph * 5.0) + 0.7 * Math.exp(-Math.pow((ph - 0.22) * 9.0, 2)));
            }
        }

        diag("drawing " + mode.value + " " + where + ", radius " + (int) radius + (ms == null ? ", no matrix stack" : ""));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        // Dome: per-vertex colours.
        for (int i = 0; i <= RINGS; i++) {
            double el = SIN_LAT[i];
            for (int j = 0; j <= SEGS; j++) {
                double az = TAU * j / SEGS;
                double dx = COS_LAT[i] * COS_AZ[j], dz = COS_LAT[i] * SIN_AZ[j];
                int c = switch (mode.value) {
                    case GALAXY -> galaxy(az, el, t);
                    case STORM -> storm(dx, el, dz, el, t, flash, boltBaseAz, az);
                    case MARBLE -> marble(dx, el, dz, el, t);
                };
                col[i * (SEGS + 1) + j] = 0xFF000000 | c;
            }
        }
        BufferBuilder q = RenderUtil.quads();
        for (int i = 0; i < RINGS; i++) {
            for (int j = 0; j < SEGS; j++) {
                vert(q, m, i, j, radius);
                vert(q, m, i, j + 1, radius);
                vert(q, m, i + 1, j + 1, radius);
                vert(q, m, i + 1, j, radius);
            }
        }
        RenderUtil.draw(q);

        if (mode.value == Mode.GALAXY) {
            if (stars.on()) drawStars(m, radius, t);
            if (planet.on()) drawPlanet(m, radius);
        } else if (mode.value == Mode.STORM && flash > 0.02f) {
            drawBolt(m, radius, flash);
        }

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        return true;
    }

    private void drawStars(Matrix4f m, float radius, float t) {
        int n = starCount.i();
        BufferBuilder sb = RenderUtil.quads();
        for (int i = 0; i < n; i++) {
            double dx = starX[i], dy = starY[i], dz = starZ[i];
            if (dy < 0.02) continue;
            float tw = 0.55f + 0.45f * (float) Math.sin(t * 2.0 + i * 1.7);
            int argb = ColorUtil.argb(0xFFFFFF, tw * (float) Math.min(1.0, dy * 4.0));
            basis(dx, dy, dz);
            double s = radius * 0.0035 * (0.7 + (i % 5) * 0.15);
            double px = dx * radius * 0.985, py = dy * radius * 0.985, pz = dz * radius * 0.985;
            for (int k = 0; k < 4; k++) {
                double su = (k == 0 || k == 3) ? -s : s;
                double sv = (k < 2) ? -s : s;
                sb.vertex(m, (float) (px + bas[0] * su + bas[3] * sv), (float) (py + bas[1] * su + bas[4] * sv),
                        (float) (pz + bas[2] * su + bas[5] * sv)).color(argb);
            }
        }
        RenderUtil.draw(sb);
    }

    // ---- ringed planet ----------------------------------------------------------------------------------------

    // Offset the planet slightly above the luminous horizon, with a broad, shallow ring plane.
    private static final double PLANET_AZ = 1.92, PLANET_EL = 0.48, TILT = -0.36, FLAT = 0.24;

    private void drawPlanet(Matrix4f m, float radius) {
        double dx = Math.cos(PLANET_EL) * Math.cos(PLANET_AZ);
        double dy = Math.sin(PLANET_EL);
        double dz = Math.cos(PLANET_EL) * Math.sin(PLANET_AZ);
        basis(dx, dy, dz);

        double dist = radius * 0.95;
        double pr = dist * Math.tan(Math.toRadians(5.5));
        double cx = dx * dist, cy = dy * dist, cz = dz * dist;

        // Saturn's rings are layered behind the globe, then overlaid in front of it.
        BufferBuilder b = RenderUtil.tris();
        ringHalf(b, m, cx, cy, cz, true);
        RenderUtil.draw(b);

        // Shaded globe with broad cream/gold atmospheric bands and a subtle polar fade.
        b = RenderUtil.tris();
        int n = 144;
        for (int k = 0; k < n; k++) {
            double a0 = TAU * k / n, a1 = TAU * (k + 1) / n;
            double am = (a0 + a1) * 0.5;
            double nx = Math.cos(am), ny = Math.sin(am);
            double latitude = ny;
            double bands = Math.sin(latitude * 36.0 + 0.85 * Math.sin(latitude * 8.5));
            int band = bands > 0.32 ? 0xF2E1B8 : bands < -0.32 ? 0xA87949 : 0xD4B47B;
            // Fine, low-contrast band detail keeps the globe from reading as a flat yellow disc.
            double fine = Math.sin(latitude * 91.0 + 0.35 * Math.sin(latitude * 17.0));
            band = ColorUtil.lerp(band, fine > 0 ? 0xE7D3A4 : 0xBD965F, 0.13f);
            double polar = Math.max(0.0, (Math.abs(latitude) - 0.78) / 0.22);
            band = ColorUtil.lerp(band, 0xEAD9B7, (float)(polar * 0.42));

            // Soft upper-left lighting and a darker lower-right limb.
            double light = Math.max(0.18, Math.min(1.0, 0.70 - nx * 0.23 + ny * 0.20));
            int shade = ColorUtil.lerp(0x463528, band, (float) light);
            int rim = ColorUtil.lerp(shade, 0xFFF0CF,
                    (float)Math.max(0.0, -nx * 0.42 + ny * 0.46) * 0.28f);

            put(b, m, cx, cy, cz, 0.0, 0.0, ColorUtil.argb(0xD4B77F, 1f));
            put(b, m, cx, cy, cz, nx * pr, ny * pr, ColorUtil.argb(rim, 1f));
            put(b, m, cx, cy, cz, Math.cos(a1) * pr, Math.sin(a1) * pr, ColorUtil.argb(shade, 1f));
        }
        RenderUtil.draw(b);

        b = RenderUtil.tris();
        ringHalf(b, m, cx, cy, cz, false);
        RenderUtil.draw(b);
    }

    /** Adds a vertex at centre + u*ox + v*oy using the current basis. */
    private void put(BufferBuilder b, Matrix4f m, double cx, double cy, double cz, double ox, double oy, int argb) {
        b.vertex(m, (float) (cx + bas[0] * ox + bas[3] * oy), (float) (cy + bas[1] * ox + bas[4] * oy),
                (float) (cz + bas[2] * ox + bas[5] * oy)).color(argb);
    }

    private void ringPoint(BufferBuilder b, Matrix4f m, double cx, double cy, double cz, double rad, double a, int argb) {
        double ex = rad * Math.cos(a), ey = rad * FLAT * Math.sin(a);
        double x = ex * Math.cos(TILT) - ey * Math.sin(TILT);
        double y = ex * Math.sin(TILT) + ey * Math.cos(TILT);
        put(b, m, cx, cy, cz, x, y, argb);
    }

    /** Render one half of Saturn's multi-band ring system, leaving a visible Cassini-like division. */
    private void ringHalf(BufferBuilder b, Matrix4f m, double cx, double cy, double cz, boolean back) {
        final double[] radii = {1.48, 1.62, 1.82, 1.96, 2.08, 2.17, 2.30, 2.48, 2.70, 2.86};
        final int[] colours = {
                0xA88C60, 0xE5D4AF, 0xB59A70, 0xD8C49A,
                0x5E4B37, // dark division between the brighter ring lanes
                0xBDA780, 0xE8D7B2, 0x9B815D, 0xD2BD92
        };
        final float[] alpha = {0.72f, 0.94f, 0.76f, 0.88f, 0.42f, 0.78f, 0.96f, 0.70f, 0.82f};
        int n = 192;
        for (int k = 0; k < n; k++) {
            double a0 = TAU * k / n, a1 = TAU * (k + 1) / n;
            double mid = (a0 + a1) * 0.5;
            boolean isBack = Math.sin(mid) >= 0;
            if (isBack != back) continue;
            double angularShade = 0.84 + 0.16 * Math.sin(mid * 3.0 + 0.4);
            for (int band = 0; band < colours.length; band++) {
                int base = colours[band];
                int colour = ColorUtil.lerp(0x554633, base, (float) angularShade);
                int argb = ColorUtil.argb(colour, alpha[band]);
                double in = radii[band], out = radii[band + 1];
                ringPoint(b, m, cx, cy, cz, in, a0, argb);
                ringPoint(b, m, cx, cy, cz, out, a0, argb);
                ringPoint(b, m, cx, cy, cz, out, a1, argb);
                ringPoint(b, m, cx, cy, cz, in, a0, argb);
                ringPoint(b, m, cx, cy, cz, out, a1, argb);
                ringPoint(b, m, cx, cy, cz, in, a1, argb);
            }
        }
    }

    // ---- lightning --------------------------------------------------------------------------------------------

    private void makeBolt(int index) {
        boltIndex = index;
        Random r = new Random(index * 7919L + 13L);
        boltBaseAz = r.nextDouble() * TAU;
        double az = boltBaseAz;
        for (int i = 0; i < boltAz.length; i++) {
            double f = i / (double) (boltAz.length - 1);
            boltEl[i] = 0.85 - f * 0.78;
            boltAz[i] = az;
            az += (r.nextDouble() - 0.5) * 0.09;
        }
    }

    private void drawBolt(Matrix4f m, float radius, float flash) {
        double dist = radius * 0.96;
        BufferBuilder b = RenderUtil.tris();
        int glowC = ColorUtil.argb(0x7CF3E6, 0.28f * flash), glowClear = ColorUtil.argb(0x7CF3E6, 0f);
        int coreC = ColorUtil.argb(0xEFFFFC, flash);
        for (int i = 0; i + 1 < boltAz.length; i++) {
            double[] p0 = dir(boltAz[i], boltEl[i]), p1 = dir(boltAz[i + 1], boltEl[i + 1]);
            double[] t0 = {-Math.sin(boltAz[i]), 0, Math.cos(boltAz[i])}, t1 = {-Math.sin(boltAz[i + 1]), 0, Math.cos(boltAz[i + 1])};
            // wide soft glow
            strip(b, m, p0, p1, t0, t1, dist, 0.075, glowClear, glowC);
            // thin bright core
            strip(b, m, p0, p1, t0, t1, dist, 0.010, coreC, coreC);
        }
        RenderUtil.draw(b);
    }

    private static double[] dir(double az, double el) {
        return new double[]{Math.cos(el) * Math.cos(az), Math.sin(el), Math.cos(el) * Math.sin(az)};
    }

    /** Quad strip between two sky points; the edges use edgeC and the centre line uses midC (so a glow fades outwards). */
    private void strip(BufferBuilder b, Matrix4f m, double[] p0, double[] p1, double[] t0, double[] t1, double dist,
                       double w, int edgeC, int midC) {
        double[] l0 = off(p0, t0, -w, dist), r0 = off(p0, t0, w, dist), l1 = off(p1, t1, -w, dist), r1 = off(p1, t1, w, dist);
        double[] c0 = {p0[0] * dist, p0[1] * dist, p0[2] * dist}, c1 = {p1[0] * dist, p1[1] * dist, p1[2] * dist};
        tri(b, m, l0, edgeC, c0, midC, c1, midC);
        tri(b, m, l0, edgeC, c1, midC, l1, edgeC);
        tri(b, m, c0, midC, r0, edgeC, r1, edgeC);
        tri(b, m, c0, midC, r1, edgeC, c1, midC);
    }

    private static double[] off(double[] p, double[] tan, double w, double dist) {
        return new double[]{(p[0] + tan[0] * w) * dist, (p[1] + tan[1] * w) * dist, (p[2] + tan[2] * w) * dist};
    }

    private static void tri(BufferBuilder b, Matrix4f m, double[] a, int ca, double[] c, int cc, double[] d, int cd) {
        b.vertex(m, (float) a[0], (float) a[1], (float) a[2]).color(ca);
        b.vertex(m, (float) c[0], (float) c[1], (float) c[2]).color(cc);
        b.vertex(m, (float) d[0], (float) d[1], (float) d[2]).color(cd);
    }

    // ---- geometry helpers -------------------------------------------------------------------------------------

    private void vert(BufferBuilder b, Matrix4f m, int i, int j, float r) {
        float x = (float) (COS_LAT[i] * COS_AZ[j] * r);
        float y = (float) (SIN_LAT[i] * r);
        float z = (float) (COS_LAT[i] * SIN_AZ[j] * r);
        b.vertex(m, x, y, z).color(col[i * (SEGS + 1) + j]);
    }

    /** Fills {@link #bas} with two unit vectors u (0..2) and v (3..5) perpendicular to direction d and to each other. */
    private void basis(double dx, double dy, double dz) {
        double rx = Math.abs(dy) > 0.95 ? 1 : 0, ry = Math.abs(dy) > 0.95 ? 0 : 1;
        double ux = ry * dz, uy = -rx * dz, uz = rx * dy - ry * dx;
        double len = Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (len < 1e-9) {
            ux = 1; uy = 0; uz = 0; len = 1;
        }
        ux /= len; uy /= len; uz /= len;
        bas[0] = ux; bas[1] = uy; bas[2] = uz;
        bas[3] = dy * uz - dz * uy;
        bas[4] = dz * ux - dx * uz;
        bas[5] = dx * uy - dy * ux;
    }
}
