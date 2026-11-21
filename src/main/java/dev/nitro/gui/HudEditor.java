package dev.nitro.gui;

import dev.nitro.Config;
import dev.nitro.module.Module;
import dev.nitro.module.ModuleManager;
import dev.nitro.util.ColorUtil;
import dev.nitro.util.Gfx;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD editor: every movable HUD panel (Target HUD, Spotify HUD, Keystrokes, Armor HUD) gets a frame you can drag.
 * Panels snap to the screen edges and centre lines (hold Alt to turn snapping off). Arrow keys nudge the panel under
 * the cursor (Shift = 5px). Right click toggles a panel on or off. Esc / Enter saves and goes back to the ClickGUI.
 */
public final class HudEditor extends Screen {
    private static final int SNAP = 5;

    private final Screen parent;
    private final List<Module> items = new ArrayList<>();
    private Module drag;
    private int grabDX, grabDY;
    private Module picked;
    private float time;

    public HudEditor(Screen parent) {
        super(Text.literal("HUD editor"));
        this.parent = parent;
        for (Module m : ModuleManager.INSTANCE.modules) if (m.movable()) items.add(m);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private Module at(double mx, double my) {
        // last drawn = on top, so search backwards
        for (int i = items.size() - 1; i >= 0; i--) {
            Module m = items.get(i);
            int[] b = m.hudBounds(width, height);
            if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) return m;
        }
        return null;
    }

    private int accent(float t) {
        float s = 0.5f + 0.5f * (float) Math.sin(time * 1.6f + t * 2.6f);
        return ColorUtil.lerp(Theme.a1(), Theme.a2(), s);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        time += delta / 20f;
        ctx.fill(0, 0, width, height, 0x66000000);

        // guide lines
        ctx.fill(width / 2, 0, width / 2 + 1, height, 0x22FFFFFF);
        ctx.fill(0, height / 2, width, height / 2 + 1, 0x22FFFFFF);

        Module hover = drag == null ? at(mx, my) : null;
        for (Module m : items) {
            int[] b = m.hudBounds(width, height);
            boolean active = m == drag || m == hover;
            int col = ColorUtil.argb(accent(b[0] / 200f), m.enabled ? (active ? 1f : 0.75f) : 0.35f);
            if (m.enabled || active) {
                Gfx.roundFill(ctx, b[0], b[1], b[2], b[3], 4, ColorUtil.argb(accent(0.2f), active ? 0.22f : 0.10f));
            }
            Gfx.roundBorder(ctx, b[0], b[1], b[2], b[3], 4, col);

            String label = m.enabled ? m.name : m.name + " (off)";
            int tw = textRenderer.getWidth(label);
            int lx = Math.max(2, Math.min(width - tw - 2, b[0] + (b[2] - tw) / 2));
            int ly = b[1] - 11 >= 2 ? b[1] - 11 : b[1] + b[3] + 3;
            ctx.drawText(textRenderer, label, lx, ly, m.enabled ? 0xFFFFFFFF : 0xFF9AA0AA, true);
        }

        Module info = drag != null ? drag : hover;
        if (info != null) {
            int[] b = info.hudBounds(width, height);
            String pos = "x " + b[0] + "   y " + b[1];
            ctx.drawCenteredTextWithShadow(textRenderer, pos, width / 2, height / 2 + 6, 0xFFFFFFFF);
        }

        ctx.drawCenteredTextWithShadow(textRenderer, "HUD editor", width / 2, 10, 0xFFFFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                "Drag a panel to move it   |   Alt: no snapping   |   Arrow keys: nudge   |   Right click: on / off   |   Esc: done",
                width / 2, 22, 0xFFB8BEC8);
        if (items.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No movable HUD panels.", width / 2, height / 2, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Module m = at(mx, my);
        if (m != null) {
            if (button == 1) {
                m.toggle();
                return true;
            }
            if (button == 0) {
                int[] b = m.hudBounds(width, height);
                drag = m;
                picked = m;
                grabDX = (int) mx - b[0];
                grabDY = (int) my - b[1];
                // bring to front
                items.remove(m);
                items.add(m);
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (drag != null && button == 0) {
            int[] b = drag.hudBounds(width, height);
            int w = b[2], h = b[3];
            int x = (int) mx - grabDX, y = (int) my - grabDY;
            if (!altDown()) {
                x = snap(x, w, width);
                y = snap(y, h, height);
            }
            x = Math.max(0, Math.min(width - w, x));
            y = Math.max(0, Math.min(height - h, y));
            drag.hudMoveTo(x, y, width, height);
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (button == 0 && drag != null) {
            drag = null;
            Config.save();
            return true;
        }
        return super.mouseReleased(mx, my, button);
    }

    /** Snaps a box edge or centre to the screen edge / centre, with a few pixels of pull. */
    private static int snap(int pos, int size, int screen) {
        if (Math.abs(pos) <= SNAP) return 0;
        if (Math.abs(pos + size - screen) <= SNAP) return screen - size;
        if (Math.abs(pos + size / 2 - screen / 2) <= SNAP) return screen / 2 - size / 2;
        return pos;
    }

    private boolean altDown() {
        long h = client.getWindow().getHandle();
        return GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER) {
            close();
            return true;
        }
        int step = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0 ? 5 : 1;
        int dx = 0, dy = 0;
        switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT -> dx = -step;
            case GLFW.GLFW_KEY_RIGHT -> dx = step;
            case GLFW.GLFW_KEY_UP -> dy = -step;
            case GLFW.GLFW_KEY_DOWN -> dy = step;
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        Module m = drag != null ? drag : picked;
        if (m == null) return true;
        int[] b = m.hudBounds(width, height);
        int x = Math.max(0, Math.min(width - b[2], b[0] + dx));
        int y = Math.max(0, Math.min(height - b[3], b[1] + dy));
        m.hudMoveTo(x, y, width, height);
        return true;
    }

    @Override
    public void close() {
        Config.save();
        if (client != null) client.setScreen(parent);
    }
}
