package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;

/**
 * Weather Control: picks the weather you see, whatever the server says. Clear hides rain, snow and thunder (and the
 * sky darkening), Rain and Thunder force them on, and Snow drifts snowflakes down around you in every biome.
 * Purely visual and only on your screen; the real weather is unchanged.
 */
public final class WeatherControl extends Module {
    public enum Mode {
        CLEAR("Clear"), RAIN("Rain"), THUNDER("Thunder"), SNOW("Snow");
        private final String label;
        Mode(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    private final Setting.Choice<Mode> mode = new Setting.Choice<>("Weather", Mode.CLEAR, Mode.class);
    private final Setting.Num density = new Setting.Num("Snow amount", 12, 1, 40, 1);

    public WeatherControl() {
        super("Weather Control", "Choose your own weather: clear, rain, thunder or snow (only on your screen).", Category.RENDER);
        add(mode, density);
        density.showIf(() -> mode.value == Mode.SNOW);
    }

    @Override
    public void onTick() {
        if (mc.world == null || mc.player == null) return;
        switch (mode.value) {
            case CLEAR, SNOW -> {
                mc.world.setRainGradient(0f);
                mc.world.setThunderGradient(0f);
            }
            case RAIN -> {
                mc.world.setRainGradient(1f);
                mc.world.setThunderGradient(0f);
            }
            case THUNDER -> {
                mc.world.setRainGradient(1f);
                mc.world.setThunderGradient(1f);
            }
        }
        if (mode.value == Mode.SNOW) spawnSnow();
    }

    /** Snowflakes in a box around the camera, only where there is open sky above. */
    private void spawnSnow() {
        Vec3d c = mc.gameRenderer.getCamera().getPos();
        Random r = mc.world.random;
        int n = density.i();
        for (int i = 0; i < n; i++) {
            double x = c.x + (r.nextDouble() - 0.5) * 32.0;
            double z = c.z + (r.nextDouble() - 0.5) * 32.0;
            double y = c.y - 2.0 + r.nextDouble() * 12.0;
            if (!mc.world.isSkyVisible(BlockPos.ofFloored(x, y, z))) continue;
            double vx = (r.nextDouble() - 0.5) * 0.05 + 0.02;
            double vz = (r.nextDouble() - 0.5) * 0.05;
            // goes straight to the particle manager so the "minimal particles" video setting does not hide it
            mc.particleManager.addParticle(ParticleTypes.SNOWFLAKE, x, y, z, vx, -0.06, vz);
        }
    }
}
