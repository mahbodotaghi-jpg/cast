package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;

/** First-person-only swing curves. This changes rendering, not attack timing or server input. */
public final class CustomAnimation extends Module {
    public enum Style {
        VANILLA("Vanilla"), SMOOTH("Smooth"), SNAP("Snappy"), FLOAT("Floaty"), SWEEP("Wide sweep");
        private final String label;
        Style(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public static CustomAnimation INSTANCE;
    private final Setting.Choice<Style> style = new Setting.Choice<>("Style", Style.SWEEP, Style.class);
    private final Setting.Num speed = new Setting.Num("Animation speed", 1.0, 0.5, 1.8, 0.05);
    private final Setting.Num intensity = new Setting.Num("Swing intensity", 1.0, 0.5, 1.5, 0.05);

    public CustomAnimation() {
        super("Custom Animation", "Changes the first-person item swing curve. Visual only.", Category.VISUALS);
        add(style, speed, intensity);
        INSTANCE = this;
    }

    public static float mapSwingProgress(float progress) {
        CustomAnimation self = INSTANCE;
        if (self == null || !self.enabled) return progress;
        float p = Math.max(0f, Math.min(1f, progress * self.speed.f()));
        float mapped = switch (self.style.value) {
            case VANILLA -> p;
            case SMOOTH -> p * p * (3f - 2f * p);
            case SNAP -> 1f - (float)Math.pow(1f - p, 3.2);
            case FLOAT -> (float)Math.sin(p * Math.PI * 0.5);
            case SWEEP -> (float)(p + 0.10 * Math.sin(Math.PI * p) * (1.0 - p));
        };
        float centered = (mapped - 0.5f) * self.intensity.f() + 0.5f;
        return Math.max(0f, Math.min(1f, centered));
    }
}
