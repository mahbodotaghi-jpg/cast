package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import net.minecraft.client.option.SimpleOption;

import java.util.HashMap;
import java.util.Map;

/**
 * No Render: an FPS booster. Switches off or turns down the expensive parts of rendering (particles, clouds,
 * shadows, smooth lighting, fancy graphics, biome blending, far entities, optionally render distance) and puts every
 * option back exactly as it was when you turn the module off, switch a part off here, or close the game.
 */
public final class NoRender extends Module {
    public static NoRender INSTANCE;

    private final Setting.Bool particles = new Setting.Bool("Minimal particles", true);
    private final Setting.Bool clouds = new Setting.Bool("No clouds", true);
    private final Setting.Bool shadows = new Setting.Bool("No entity shadows", true);
    private final Setting.Bool fastGraphics = new Setting.Bool("Fast graphics", true);
    private final Setting.Bool smoothLight = new Setting.Bool("No smooth lighting", false);
    private final Setting.Bool biomeBlend = new Setting.Bool("No biome blending", true);
    private final Setting.Bool bobbing = new Setting.Bool("No view bobbing", false);
    private final Setting.Bool entityDist = new Setting.Bool("Shorter entity distance", true);
    private final Setting.Num entityPct = new Setting.Num("Entity distance (%)", 50, 50, 200, 10);
    private final Setting.Bool limitView = new Setting.Bool("Limit render distance", false);
    private final Setting.Num viewDist = new Setting.Num("Render distance (chunks)", 8, 2, 32, 1);

    /** One undo action per feature that is currently applied. */
    private final Map<String, Runnable> undo = new HashMap<>();
    private int ticks;

    public NoRender() {
        super("No Render", "FPS boost: turns down particles, clouds, shadows, lighting and other costly rendering.", Category.RENDER);
        add(particles, clouds, shadows, fastGraphics, smoothLight, biomeBlend, bobbing, entityDist, entityPct, limitView, viewDist);
        entityPct.showIf(entityDist::on);
        viewDist.showIf(limitView::on);
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        ticks = 0;
        apply();
    }

    @Override
    public void onDisable() {
        undoAll();
    }

    @Override
    public void onTick() {
        // re-check a few times a second so toggling a part in the GUI takes effect right away
        if (++ticks % 5 == 0) apply();
    }

    private void apply() {
        if (mc.options == null) return;
        var o = mc.options;
        driveEnum("particles", particles.on(), o.getParticles(), "MINIMAL");
        driveEnum("clouds", clouds.on(), o.getCloudRenderMode(), "OFF");
        drive("shadows", shadows.on(), o.getEntityShadows(), false);
        driveEnum("graphics", fastGraphics.on(), o.getGraphicsMode(), "FAST");
        drive("ao", smoothLight.on(), o.getAo(), false);
        drive("blend", biomeBlend.on(), o.getBiomeBlendRadius(), 0);
        drive("bob", bobbing.on(), o.getBobView(), false);
        drive("entities", entityDist.on(), o.getEntityDistanceScaling(), entityPct.d() / 100.0);
        drive("view", limitView.on(), o.getViewDistance(), viewDist.i());
    }

    /** Applies {@code value} to the option while {@code on}; restores the original value when it goes off. */
    private <T> void drive(String key, boolean on, SimpleOption<T> opt, T value) {
        if (on) {
            if (!undo.containsKey(key)) {
                final T old = opt.getValue();
                undo.put(key, () -> opt.setValue(old));
            }
            if (!opt.getValue().equals(value)) opt.setValue(value);
        } else {
            Runnable r = undo.remove(key);
            if (r != null) r.run();
        }
    }

    /** Same as {@link #drive}, for enum options: picks the constant by its name (no need to import the enum class). */
    private <E extends Enum<E>> void driveEnum(String key, boolean on, SimpleOption<E> opt, String constant) {
        E target = null;
        for (E c : opt.getValue().getDeclaringClass().getEnumConstants()) {
            if (c.name().equals(constant)) target = c;
        }
        drive(key, on && target != null, opt, target);
    }

    private void undoAll() {
        for (Runnable r : undo.values()) r.run();
        undo.clear();
    }

    /** Called when the client is shutting down so options.txt keeps your real settings. */
    public static void restore() {
        if (INSTANCE != null) INSTANCE.undoAll();
    }
}
