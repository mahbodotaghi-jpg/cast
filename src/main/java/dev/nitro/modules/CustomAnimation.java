package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;

/**
 * Client-side first-person swing animation styles. This changes only the local render progress;
 * it does not alter attack timing or the packets sent to a server.
 */
public final class CustomAnimation extends Module {
    public enum Style {
        VANILLA("Vanilla"), SMOOTH("Smooth"), SNAP("Snappy"), FLOAT("Floaty");
        private final String label;
        Style(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public static CustomAnimation INSTANCE;

    private final Setting.Choice<Style> style = new Setting.Choice<>("Style", Style.SMOOTH, Style.class);
    private final Setting.Num speed = new Setting.Num("Animation speed", 1.0, 0.5, 1.8, 0.05);

    public CustomAnimation() {
        super("Custom Animation", "Changes the first-person swing animation style. Visual only.", Category.RENDER);
        add(style, speed);
        INSTANCE = this;
    }

    /** Called by HeldItemRendererMixin to remap the vanilla 0..1 animation curve. */
    public static float mapSwingProgress(float vanillaProgress) {
        CustomAnimation self = INSTANCE;
        if (self == null || !self.enabled || vanillaProgress <= 0f) return vanillaProgress;

        float p = Math.max(0f, Math.min(1f, vanillaProgress * self.speed.f()));
        return switch (self.style.value) {
            case VANILLA -> vanillaProgress;
            case SMOOTH -> p * p * (3f - 2f * p);
            case SNAP -> 1f - (float) Math.pow(1f - p, 3.0);
            case FLOAT -> (float) (0.5 - 0.5 * Math.cos(Math.PI * p));
        };
    }
}
