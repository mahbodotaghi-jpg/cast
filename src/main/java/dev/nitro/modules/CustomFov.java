package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;

/** Smooth local FOV override. */
public final class CustomFov extends Module {
    public static CustomFov INSTANCE;
    private final Setting.Num fov = new Setting.Num("Field of view", 100, 30, 130, 1);
    private final Setting.Num smoothing = new Setting.Num("Smoothing", 8, 1, 20, 1);
    private float current = 70f;

    public CustomFov() {
        super("Custom FOV", "Sets a smoothly animated field of view.", Category.VISUALS);
        add(fov, smoothing);
        INSTANCE = this;
    }

    @Override public void onEnable() { current = fov.f(); }

    @Override public void onTick() {
        float target = fov.f();
        current += (target - current) * Math.min(1f, 0.04f * smoothing.f());
    }

    public static float apply(float vanilla) {
        return INSTANCE != null && INSTANCE.enabled ? INSTANCE.current : vanilla;
    }
}
