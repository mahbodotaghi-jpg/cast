package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;

/** Recolours the local item enchantment glint through the ItemRenderer glint buffer. */
public final class CustomGlint extends Module {
    public enum Style {
        GALAXY("Galaxy"), RAINBOW("Rainbow"), CYAN("Cyan"), GOLD("Gold"), PINK("Pink"), CUSTOM("Custom");
        private final String label;
        Style(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public static CustomGlint INSTANCE;
    private final Setting.Choice<Style> style = new Setting.Choice<>("Style", Style.GALAXY, Style.class);
    private final Setting.Num speed = new Setting.Num("Colour speed", 1.0, 0.1, 4.0, 0.1);
    private final Setting.Color custom = new Setting.Color("Custom colour", 0xB77CFF);

    public CustomGlint() {
        super("Custom Glint", "Changes the colour of enchanted-item shimmer locally.", Category.VISUALS);
        add(style, speed, custom);
        INSTANCE = this;
    }

    public static boolean active() { return INSTANCE != null && INSTANCE.enabled; }

    public static int tintRgb() {
        if (!active()) return 0xFFFFFF;
        CustomGlint self = INSTANCE;
        float t = (System.currentTimeMillis() % 120000L) / 120000f * self.speed.f();
        return switch (self.style.value) {
            case GALAXY -> blend(0x7A36FF, 0x35D9FF, (float)(0.5 + 0.5 * Math.sin(t * Math.PI * 2.0)));
            case RAINBOW -> hsv(t, 0.85f, 1f);
            case CYAN -> blend(0x167DFF, 0x6BFFFF, (float)(0.5 + 0.5 * Math.sin(t * Math.PI * 2.0)));
            case GOLD -> blend(0x8B572A, 0xFFE7A3, (float)(0.5 + 0.5 * Math.sin(t * Math.PI * 2.0)));
            case PINK -> blend(0xA12CFF, 0xFF79D6, (float)(0.5 + 0.5 * Math.sin(t * Math.PI * 2.0)));
            case CUSTOM -> self.custom.rgb();
        };
    }

    private static int blend(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) * (1f - f) + ((b >> 16) & 255) * f);
        int g = Math.round(((a >> 8) & 255) * (1f - f) + ((b >> 8) & 255) * f);
        int bl = Math.round((a & 255) * (1f - f) + (b & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }

    private static int hsv(float h, float s, float v) {
        h = h - (float)Math.floor(h);
        float c = v * s, x = c * (1f - Math.abs((h * 6f) % 2f - 1f)), m = v - c;
        float r=0,g=0,b=0; int seg=(int)(h*6f);
        switch (seg) {
            case 0 -> { r=c; g=x; }
            case 1 -> { r=x; g=c; }
            case 2 -> { g=c; b=x; }
            case 3 -> { g=x; b=c; }
            case 4 -> { r=x; b=c; }
            default -> { r=c; b=x; }
        }
        return (Math.round((r+m)*255f)<<16) | (Math.round((g+m)*255f)<<8) | Math.round((b+m)*255f);
    }
}
