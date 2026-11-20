package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import net.minecraft.client.gui.DrawContext;

/** Custom local HUD crosshair with selectable styles and colour. */
public final class CustomCrosshair extends Module {
    public enum Style {
        PLUS("Plus"), CROSS("Cross"), DOT("Dot"), CIRCLE("Circle"), T("T shape");
        private final String label;
        Style(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public static CustomCrosshair INSTANCE;
    private final Setting.Choice<Style> style = new Setting.Choice<>("Style", Style.CROSS, Style.class);
    private final Setting.Num size = new Setting.Num("Size", 5, 2, 12, 1);
    private final Setting.Num gap = new Setting.Num("Gap", 3, 0, 10, 1);
    private final Setting.Num thickness = new Setting.Num("Thickness", 2, 1, 5, 1);
    private final Setting.Color color = new Setting.Color("Colour", 0xFFFFFF);
    private final Setting.Bool hideVanilla = new Setting.Bool("Hide vanilla crosshair", true);

    public CustomCrosshair() {
        super("Custom Crosshair", "Draws a configurable crosshair in the centre of your screen.", Category.VISUALS);
        add(style, size, gap, thickness, color, hideVanilla);
        INSTANCE = this;
    }

    public static boolean hideVanilla() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.hideVanilla.on();
    }

    @Override public void renderHud(DrawContext ctx) {
        if (mc.player == null || mc.options.hudHidden) return;
        int cx = ctx.getScaledWindowWidth() / 2;
        int cy = ctx.getScaledWindowHeight() / 2;
        int s = size.i(), g = gap.i(), t = thickness.i();
        int col = 0xFF000000 | color.rgb();
        switch (style.value) {
            case DOT -> ctx.fill(cx - t / 2, cy - t / 2, cx + (t + 1) / 2, cy + (t + 1) / 2, col);
            case CIRCLE -> {
                int r = Math.max(3, s);
                for (int i = 0; i < 32; i++) {
                    double a = Math.PI * 2.0 * i / 32.0;
                    int x = cx + (int)Math.round(Math.cos(a) * r);
                    int y = cy + (int)Math.round(Math.sin(a) * r);
                    ctx.fill(x, y, x + t, y + t, col);
                }
            }
            case PLUS -> {
                ctx.fill(cx - t / 2, cy - s - g, cx + (t + 1) / 2, cy + s + g + 1, col);
                ctx.fill(cx - s - g, cy - t / 2, cx + s + g + 1, cy + (t + 1) / 2, col);
            }
            case CROSS -> {
                ctx.fill(cx - t / 2, cy - s - g, cx + (t + 1) / 2, cy - g, col);
                ctx.fill(cx - t / 2, cy + g + 1, cx + (t + 1) / 2, cy + s + g + 1, col);
                ctx.fill(cx - s - g, cy - t / 2, cx - g, cy + (t + 1) / 2, col);
                ctx.fill(cx + g + 1, cy - t / 2, cx + s + g + 1, cy + (t + 1) / 2, col);
            }
            case T -> {
                ctx.fill(cx - t / 2, cy - s - g, cx + (t + 1) / 2, cy + s + 1, col);
                ctx.fill(cx - s - g, cy - s - g, cx + s + g + 1, cy - s - g + t, col);
            }
        }
    }
}
