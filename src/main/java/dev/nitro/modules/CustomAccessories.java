package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/** Client-only decorative finish for tools. It never changes item data or server state. */
public final class CustomAccessories extends Module {
    public enum Finish {
        GALAXY("Galaxy"), RAINBOW("Rainbow"), AURORA("Aurora"), GOLD("Gold");
        private final String label;
        Finish(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

    public static CustomAccessories INSTANCE;
    private final Setting.Choice<Finish> finish = new Setting.Choice<>("Tool finish", Finish.GALAXY, Finish.class);
    private final Setting.Num speed = new Setting.Num("Animation speed", 1.0, 0.1, 4.0, 0.1);
    private final Setting.Bool enchantTools = new Setting.Bool("Show shimmer on tools", true);

    public CustomAccessories() {
        super("Custom Accessories", "Adds a local-only animated Galaxy, Rainbow, Aurora or Gold shimmer to supported tools. The original tool texture stays intact; no item data or packets are changed.", Category.VISUALS);
        add(finish, speed, enchantTools);
        INSTANCE = this;
    }

    public static boolean active() { return INSTANCE != null && INSTANCE.enabled; }

    public static boolean isTool(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        String id = Registries.ITEM.getId(stack.getItem()).getPath();
        return id.endsWith("_sword") || id.endsWith("_pickaxe") || id.endsWith("_axe")
                || id.endsWith("_shovel") || id.endsWith("_hoe") || id.equals("mace")
                || id.equals("trident");
    }

    public static boolean shouldGiveGlint(ItemStack stack) {
        return active() && INSTANCE.enchantTools.on() && isTool(stack);
    }

    public static int tintRgb() {
        if (!active()) return 0xFFFFFF;
        CustomAccessories self = INSTANCE;
        float t = (System.currentTimeMillis() % 120000L) / 120000f * self.speed.f();
        return switch (self.finish.value) {
            case GALAXY -> blend(0x5021A8, 0x39D6FF, (float)(0.5 + 0.5 * Math.sin(t * Math.PI * 2.0)));
            case RAINBOW -> hsv(t, 0.95f, 1f);
            case AURORA -> blend(0x19D6B4, 0xA34CFF, (float)(0.5 + 0.5 * Math.sin(t * Math.PI * 2.0)));
            case GOLD -> blend(0x97602D, 0xFFE8A8, (float)(0.5 + 0.5 * Math.sin(t * Math.PI * 2.0)));
        };
    }

    private static int blend(int a, int b, float f) {
        int r = Math.round(((a >> 16) & 255) * (1f-f) + ((b >> 16) & 255) * f);
        int g = Math.round(((a >> 8) & 255) * (1f-f) + ((b >> 8) & 255) * f);
        int bl = Math.round((a & 255) * (1f-f) + (b & 255) * f);
        return (r << 16) | (g << 8) | bl;
    }

    private static int hsv(float h, float s, float v) {
        h -= (float)Math.floor(h);
        float c=v*s, x=c*(1f-Math.abs((h*6f)%2f-1f)), m=v-c;
        float r=0,g=0,b=0;
        switch ((int)(h*6f)) {
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
