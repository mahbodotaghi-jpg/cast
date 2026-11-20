package dev.nitro.gui;

import dev.nitro.Config;
import dev.nitro.module.Module;
import dev.nitro.module.ModuleManager;
import dev.nitro.settings.Setting;
import dev.nitro.util.ColorUtil;
import dev.nitro.util.Gfx;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * NITRO ClickGUI (panel layout): header with logo, search and window buttons; a category sidebar with
 * Settings / Themes at the bottom; and a grid of category panels with a switch per module.
 * Click a module name for its settings page, right click (or the switch) to toggle it.
 */
public final class ClickGui extends Screen {
    private static final int SIDE = 128, HEAD = 48, LIST_W = 200, CARD_H = 38, GAP = 4;
    private static final int PANEL_HEAD = 28, ROW_H = 21, PGAP = 8;

    private static final String VERSION = FabricLoader.getInstance().getModContainer("nitro")
            .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString()).orElse("v?") + " | 1.21.4";

    private enum Page { MODULES, MODULE, THEMES, SETTINGS }

    private Page page = Page.MODULES;
    private String sideSel = "Combat";
    private boolean minimized;
    private Module selected;
    private Module hoverModule;
    private int listScroll, setScroll;
    private float scroll;
    private int scrollTarget;
    private boolean binding;
    private Setting.Str textFocus;
    private String search = "";
    private boolean searchFocus;

    private Setting.Num dragNum;
    private int dragX0, dragW;
    private Setting.Color dragColor;
    private int dragCh;

    private final Map<Object, Float> anim = new HashMap<>();
    private final long openedAt = System.nanoTime();
    private long lastFrame = openedAt;
    private float time, dt, alpha, slide, tabY = -1;

    private int wx, wy, ww, wh;

    private enum Kind { ENABLE, BIND, SETTING, DISABLE_ALL }

    private record Row(Kind kind, Setting<?> s, int y, int h) {}

    private static final class Panel {
        final String title;
        final ItemStack icon;
        final List<Module> mods;
        int col, y, h;

        Panel(String title, ItemStack icon, List<Module> mods) {
            this.title = title;
            this.icon = icon;
            this.mods = mods;
        }
    }

    public ClickGui() {
        super(Text.literal("NITRO"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // ------------------------------------------------------------ geometry

    private void layout() {
        time = (System.nanoTime() - openedAt) / 1e9f;
        float p = Math.min(1f, time / 0.28f);
        float e = 1f - (1f - p) * (1f - p) * (1f - p);
        alpha = e;
        slide = (1f - e) * 16f;
        ww = Math.min(width - 20, 690);
        int full = Math.min(height - 20, 400);
        wh = minimized ? HEAD + 4 : full;
        wx = (width - ww) / 2;
        wy = (height - full) / 2 + Math.round(slide);
    }

    private int contentX() { return wx + SIDE + 10; }
    private int contentR() { return wx + ww - 12; }
    private int listX() { return contentX(); }
    private int setX() { return page == Page.THEMES ? listX() + LIST_W + 6 : contentX(); }
    private int setR() { return contentR(); }
    private int top() { return wy + HEAD; }
    private int bot() { return wy + wh - 10; }
    private int closeX() { return wx + ww - 12 - 18; }
    private int minX() { return closeX() - 22; }
    private int searchX() { return minX() - 10 - 150; }
    private int bottomY(int k) { return bot() - 2 * 24 - 2 + k * 24; }

    // ------------------------------------------------------------ colour helpers

    private int accent(float t) {
        float phase = Theme.ANIMATED.on() ? time * 1.1f : 0f;
        float s = 0.5f + 0.5f * (float) Math.sin(phase + t * 2.6f);
        return ColorUtil.lerp(Theme.a1(), Theme.a2(), s);
    }

    private int ac(float t, float a) {
        return ColorUtil.argb(accent(t), a * alpha);
    }

    private int A(int argb) {
        return Gfx.mulA(argb, alpha);
    }

    private float an(Object key, boolean on) {
        float cur = anim.getOrDefault(key, on ? 1f : 0f);
        float target = on ? 1f : 0f;
        cur += (target - cur) * Math.min(1f, dt * 14f);
        if (Math.abs(target - cur) < 0.005f) cur = target;
        anim.put(key, cur);
        return cur;
    }

    private void text(DrawContext c, String s, int x, int y, int argb) {
        if (((argb >>> 24) & 0xFF) < 8) return;
        c.drawText(textRenderer, s, x, y, argb, false);
    }

    private void small(DrawContext c, String s, float x, float y, float scale, int argb) {
        if (((argb >>> 24) & 0xFF) < 8) return;
        Gfx.text(c, textRenderer, s, x, y, scale, argb, false);
    }

    private void sw(DrawContext c, int x, int y, Object key, boolean on) {
        float t = an(key, on);
        int track = Gfx.lerpArgb(A(Theme.track()), ac(x / 120f, 1f), t);
        Gfx.roundFill(c, x, y, 26, 14, 7, track);
        int kx = x + 2 + Math.round(12 * t);
        Gfx.roundFill(c, kx, y + 2, 10, 10, 5, A(0xFFF8F8FC));
    }

    // ------------------------------------------------------------ content model

    private void icon(DrawContext c, ItemStack s, int x, int y, float scale) {
        var ms = c.getMatrices();
        ms.push();
        ms.translate(x, y, 0f);
        ms.scale(scale, scale, 1f);
        c.drawItem(s, 0, 0);
        ms.pop();
    }

    private static Item iconOf(Module.Category c) {
        return switch (c) {
            case COMBAT -> Items.DIAMOND_SWORD;
            case RENDER -> Items.ENDER_EYE;
            case WORLD -> Items.GRASS_BLOCK;
            case HUD -> Items.COMPASS;
            default -> Items.BOOK;
        };
    }

    /** Categories that have modules, Combat first. */
    private List<Module.Category> orderedCats() {
        List<Module.Category> all = new ArrayList<>(List.of(Module.Category.values()));
        all.remove(Module.Category.COMBAT);
        all.add(0, Module.Category.COMBAT);
        List<Module.Category> out = new ArrayList<>();
        for (Module.Category c : all) {
            for (Module m : ModuleManager.INSTANCE.modules) {
                if (m.category == c) {
                    out.add(c);
                    break;
                }
            }
        }
        return out;
    }

    private List<String> sideLabels() {
        List<String> l = new ArrayList<>();
        for (Module.Category c : orderedCats()) l.add(c.label);
        return l;
    }

    private Item sideIcon(String label) {
        for (Module.Category c : Module.Category.values()) if (c.label.equals(label)) return iconOf(c);
        return Items.BOOK;
    }

    private static int panelH(int n) {
        return PANEL_HEAD + Math.max(1, n) * ROW_H + 8;
    }

    /** All panels with their column and y (relative to the top of the scrolled content). */
    private List<Panel> panels() {
        List<Panel> out = new ArrayList<>();
        String q = search.trim().toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            List<Module> res = new ArrayList<>();
            for (Module m : ModuleManager.INSTANCE.modules) {
                if (m.name.toLowerCase(Locale.ROOT).contains(q) || m.description.toLowerCase(Locale.ROOT).contains(q)) res.add(m);
            }
            Panel p = new Panel("Results", new ItemStack(Items.SPYGLASS), res);
            p.col = -1;
            p.y = 0;
            p.h = panelH(res.size());
            out.add(p);
            return out;
        }
        // Each sidebar category is its own full-width section. Search remains global.
        for (Module.Category c : orderedCats()) {
            if (!c.label.equals(sideSel)) continue;
            List<Module> l = new ArrayList<>();
            for (Module m : ModuleManager.INSTANCE.modules) if (m.category == c) l.add(m);
            Panel p = new Panel(c.label, new ItemStack(iconOf(c)), l);
            p.col = -1;
            p.y = 0;
            p.h = panelH(p.mods.size());
            out.add(p);
        }
        return out;
    }

    private int totalHeight() {
        int h = 0;
        for (Panel p : panels()) h = Math.max(h, p.y + p.h);
        return h;
    }

    private int maxScroll() {
        return Math.max(0, totalHeight() + 12 - (bot() - top()));
    }

    private void goPanel(String label) {
        search = "";
        searchFocus = false;
        page = Page.MODULES;
        sideSel = label;
        setScroll = 0;
        int y = 0;
        for (Panel p : panels()) {
            if (p.title.equals(label)) {
                y = p.y;
                break;
            }
        }
        scrollTarget = Math.max(0, Math.min(maxScroll(), y));
    }

    private void openPage(Page p) {
        page = page == p ? Page.MODULES : p;
        setScroll = 0;
        listScroll = 0;
        search = "";
        searchFocus = false;
        textFocus = null;
        binding = false;
    }

    private void disableAll() {
        for (Module m : ModuleManager.INSTANCE.modules) m.setEnabled(false);
    }

    private String themeDesc() {
        return Theme.PRESET.value.blurb + ". Changes apply instantly and are saved with your config.";
    }

    private String paneTitle() {
        return switch (page) {
            case MODULE -> selected == null ? "" : selected.name;
            case THEMES -> Theme.PRESET.value.label + " theme";
            default -> "Settings";
        };
    }

    private String paneDesc() {
        return switch (page) {
            case MODULE -> selected == null ? "" : selected.description;
            case THEMES -> themeDesc();
            default -> "Interface options and quick actions. Right Shift opens and closes this window.";
        };
    }

    private int paneHeaderHeight() {
        int lines = textRenderer.wrapLines(Text.literal(paneDesc()), setR() - setX() - 28).size();
        return 30 + lines * 10 + 8 + (page == Page.THEMES ? 14 : 0);
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        int y = top() + 6 + paneHeaderHeight() - setScroll;
        if (page == Page.THEMES) {
            for (Setting<?> s : Theme.settings()) {
                if (!s.visible.getAsBoolean()) continue;
                int h = rowHeight(s);
                rows.add(new Row(Kind.SETTING, s, y, h));
                y += h + GAP;
            }
        } else if (page == Page.SETTINGS) {
            int h = rowHeight(Theme.ANIMATED);
            rows.add(new Row(Kind.SETTING, Theme.ANIMATED, y, h));
            y += h + GAP;
            rows.add(new Row(Kind.DISABLE_ALL, null, y, 26));
        } else if (page == Page.MODULE && selected != null) {
            rows.add(new Row(Kind.ENABLE, null, y, 28)); y += 32;
            rows.add(new Row(Kind.BIND, null, y, 26)); y += 34;
            for (Setting<?> s : selected.settings) {
                if (!s.visible.getAsBoolean()) continue;
                int h = rowHeight(s);
                rows.add(new Row(Kind.SETTING, s, y, h));
                y += h + GAP;
            }
        }
        return rows;
    }

    private int contentHeight() {
        List<Row> r = rows();
        if (r.isEmpty()) return 0;
        Row last = r.get(r.size() - 1);
        return last.y() + last.h() + setScroll - top();
    }

    private int rowHeight(Setting<?> s) {
        if (s instanceof Setting.Num) return 36;
        if (s instanceof Setting.Color) return 44;
        if (s instanceof Setting.Str) return 44;
        if (s instanceof Setting.BlockSet) return BS_H;
        if (s instanceof Setting.Picks p) {
            int cols = pickCols(p, setR() - setX() - 24);
            return 22 + ((p.icons.length + cols - 1) / cols) * 24 + 4;
        }
        if (s instanceof Setting.Choice<?> c) return 24 + c.options().length * 15;
        return 26;
    }

    private static final int BS_H = 22 + 22 + Setting.BlockSet.ROWS * 24 + 24;

    private static int bsCols(int iw) {
        return Math.max(1, (iw - 16) / 24);
    }

    private static int pickCols(Setting.Picks p, int iw) {
        return Math.max(1, Math.min(p.perRow, (iw - 16) / 24));
    }

    // ------------------------------------------------------------ render

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        layout();
        long now = System.nanoTime();
        dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        hoverModule = null;

        ctx.fillGradient(0, 0, width, height, A(Theme.backTop()), A(Theme.backBot()));

        Gfx.glow(ctx, wx, wy, ww, wh, 12, accent(0.4f), 0.32f * alpha);
        Gfx.roundGradientV(ctx, wx, wy, ww, wh, 12, A(Theme.winTop()), A(Theme.winBot()));
        Gfx.roundBorder(ctx, wx, wy, ww, wh, 12, ac(0.2f, 0.55f));

        drawHeader(ctx, mx, my);
        if (minimized) return;

        ctx.fill(wx + 1, wy + HEAD - 1, wx + ww - 1, wy + HEAD, A(0x1EFFFFFF));
        ctx.fill(wx + SIDE, wy + HEAD, wx + SIDE + 1, wy + wh - 8, A(0x1EFFFFFF));
        drawSidebar(ctx, mx, my);

        switch (page) {
            case MODULES -> drawPanels(ctx, mx, my);
            case THEMES -> {
                drawThemeList(ctx, mx, my);
                drawPane(ctx, mx, my);
            }
            default -> drawPane(ctx, mx, my);
        }

        if (hoverModule != null && page == Page.MODULES && !hoverModule.description.isEmpty()) {
            List<OrderedText> tip = textRenderer.wrapLines(Text.literal(hoverModule.description), 200);
            ctx.drawOrderedTooltip(textRenderer, tip, mx, my);
        }
    }

    private void drawHeader(DrawContext ctx, int mx, int my) {
        // logo tile + title
        Gfx.roundGradientV(ctx, wx + 14, wy + 10, 28, 28, 8, ac(0.1f, 1f), ac(0.9f, 1f));
        Gfx.text(ctx, textRenderer, "N", wx + 14 + 9, wy + 10 + 6, 2f, A(0xFFFFFFFF), false);
        Gfx.text(ctx, textRenderer, "Nitro Client", wx + 50, wy + 11, 1.5f, A(Theme.text()), false);
        small(ctx, VERSION, wx + 50, wy + 28, 0.8f, A(Theme.dim()));

        // search
        int sx = searchX(), sy = wy + 14, sw = 150, sh = 20;
        float f = an("search", searchFocus);
        Gfx.roundFill(ctx, sx, sy, sw, sh, 6, A(0x66000000));
        Gfx.roundBorder(ctx, sx, sy, sw, sh, 6, Gfx.lerpArgb(A(0x33FFFFFF), ac(0.6f, 0.9f), f));
        boolean empty = search.isEmpty();
        String shown = empty ? (searchFocus ? "" : "Search modules...") : search;
        while (textRenderer.getWidth(shown) > sw - 16) shown = shown.substring(1);
        text(ctx, shown, sx + 8, sy + 6, A(empty ? Theme.faint() : Theme.text()));
        if (searchFocus && (int) (time * 2) % 2 == 0) {
            int cx = sx + 8 + textRenderer.getWidth(shown);
            ctx.fill(cx, sy + 5, cx + 1, sy + 15, A(0xFFFFFFFF));
        }

        // minimize + close
        int cy = wy + 15, size = 18;
        boolean hMin = mx >= minX() && mx < minX() + size && my >= cy && my < cy + size;
        boolean hClose = mx >= closeX() && mx < closeX() + size && my >= cy && my < cy + size;
        float a1 = an("hmin", hMin), a2 = an("hclose", hClose);
        if (a1 > 0.02f) Gfx.roundFill(ctx, minX(), cy, size, size, 5, A(Gfx.mulA(0x33FFFFFF, a1)));
        ctx.fill(minX() + 5, cy + 12, minX() + 13, cy + 14, A(Theme.soft()));
        if (a2 > 0.02f) Gfx.roundFill(ctx, closeX(), cy, size, size, 5, A(Gfx.mulA(0x77EF4444, a2)));
        int xc = A(Theme.soft());
        for (int i = 0; i < 8; i++) {
            ctx.fill(closeX() + 5 + i, cy + 5 + i, closeX() + 6 + i, cy + 6 + i, xc);
            ctx.fill(closeX() + 12 - i, cy + 5 + i, closeX() + 13 - i, cy + 6 + i, xc);
        }
    }

    private void drawSidebar(DrawContext ctx, int mx, int my) {
        List<String> labels = sideLabels();
        int tx = wx + 8, tw = SIDE - 16;
        boolean searching = !search.trim().isEmpty();
        int selIdx = -1;
        if (page != Page.THEMES && page != Page.SETTINGS && !searching) selIdx = labels.indexOf(sideSel);

        float target = top() + 8 + Math.max(0, selIdx) * 30;
        tabY = tabY < 0 ? target : tabY + (target - tabY) * Math.min(1f, dt * 16f);
        if (selIdx >= 0) Gfx.roundGradientV(ctx, tx, Math.round(tabY), tw, 26, 7, ac(0.1f, 0.95f), ac(0.8f, 0.95f));

        for (int i = 0; i < labels.size(); i++) {
            int ty = top() + 8 + i * 30;
            boolean sel = i == selIdx;
            boolean hover = mx >= tx && mx < tx + tw && my >= ty && my < ty + 26;
            icon(ctx, new ItemStack(sideIcon(labels.get(i))), tx + 8, ty + 5, 1f);
            text(ctx, labels.get(i), tx + 30, ty + 9, A(sel ? 0xFFFFFFFF : hover ? Theme.soft() : Theme.dim()));
        }

        // bottom group: Settings / Themes
        ctx.fill(tx, bottomY(0) - 5, tx + tw, bottomY(0) - 4, A(0x1EFFFFFF));
        String[] names = {"Settings", "Themes"};
        Item[] icons = {Items.COMPARATOR, Items.PAINTING};
        for (int k = 0; k < 2; k++) {
            int by = bottomY(k);
            boolean active = (k == 0 && page == Page.SETTINGS) || (k == 1 && page == Page.THEMES);
            boolean hover = mx >= tx && mx < tx + tw && my >= by && my < by + 22;
            if (active) Gfx.roundFill(ctx, tx, by, tw, 22, 6, ac(0.1f, 0.30f));
            icon(ctx, new ItemStack(icons[k]), tx + 8, by + 4, 0.88f);
            text(ctx, names[k], tx + 28, by + 7, A(active ? 0xFFFFFFFF : hover ? Theme.soft() : Theme.dim()));
        }
    }

    private void drawPanels(DrawContext ctx, int mx, int my) {
        scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget));
        scroll += (scrollTarget - scroll) * Math.min(1f, dt * 14f);
        if (Math.abs(scrollTarget - scroll) < 0.5f) scroll = scrollTarget;

        int cx0 = contentX(), cw = contentR() - cx0, colW = (cw - PGAP) / 2;
        int oy = top() + 6 - Math.round(scroll);
        boolean inView = mx >= cx0 && mx < contentR() && my >= top() && my < bot();

        ctx.enableScissor(cx0 - 2, top() + 1, contentR() + 2, bot() - 1);
        for (Panel p : panels()) {
            int px = p.col < 0 ? cx0 : cx0 + p.col * (colW + PGAP);
            int pw = p.col < 0 ? cw : colW;
            int py = oy + p.y;
            if (py + p.h < top() || py > bot()) continue;

            Gfx.roundFill(ctx, px, py, pw, p.h, 9, A(0x14FFFFFF));
            Gfx.roundBorder(ctx, px, py, pw, p.h, 9, A(0x26FFFFFF));
            icon(ctx, p.icon, px + 10, py + 7, 0.9f);
            Gfx.text(ctx, textRenderer, p.title, px + 30, py + 10, 1.1f, A(Theme.text()), false);

            if (p.mods.isEmpty()) {
                text(ctx, "No match", px + 12, py + PANEL_HEAD + 7, A(Theme.dim()));
            }
            for (int i = 0; i < p.mods.size(); i++) {
                Module m = p.mods.get(i);
                int ry = py + PANEL_HEAD + i * ROW_H;
                boolean hover = inView && mx >= px + 4 && mx < px + pw - 4 && my >= ry && my < ry + ROW_H;
                float hv = an(m.name + "#row", hover);
                if (hv > 0.02f) Gfx.roundFill(ctx, px + 4, ry, pw - 8, ROW_H - 1, 5, A(Gfx.mulA(0x26FFFFFF, hv)));
                float on = an(m, m.enabled);
                text(ctx, m.name, px + 12, ry + 7, A(Gfx.lerpArgb(Theme.soft(), 0xFFFFFFFF, on)));
                sw(ctx, px + pw - 38, ry + 3, m, m.enabled);
                if (hover && mx < px + pw - 44) hoverModule = m;
            }
        }
        ctx.disableScissor();
    }

    private void drawThemeList(DrawContext ctx, int mx, int my) {
        int lx = listX() + 2, lw = LIST_W - 4;
        Gfx.roundFill(ctx, lx, top(), lw, bot() - top(), 9, A(0x55000000));
        ctx.enableScissor(lx, top() + 1, lx + lw, bot() - 1);
        int y = top() + 6 - listScroll;
        for (Theme.Preset p : Theme.Preset.values()) {
            int cx = lx + 6, cw = lw - 12;
            boolean sel = Theme.PRESET.value == p;
            boolean hover = mx >= cx && mx < cx + cw && my >= y && my < y + CARD_H && my >= top() && my < bot();
            float on = an("theme#" + p.name(), sel);
            float hv = an("theme#h" + p.name(), hover);
            int base = Gfx.lerpArgb(A(0x33FFFFFF), ac(0.3f, 0.20f), on);
            base = Gfx.lerpArgb(base, A(0x4DFFFFFF), hv * 0.5f);
            Gfx.roundFill(ctx, cx, y, cw, CARD_H, 8, base);
            if (sel) Gfx.roundBorder(ctx, cx, y, cw, CARD_H, 8, ac(0.5f, 0.9f));
            if (on > 0.02f) Gfx.roundFill(ctx, cx, y + 6, 3, CARD_H - 12, 1, ac(y / 140f, on));
            text(ctx, p.label, cx + 10, y + 6, A(Gfx.lerpArgb(Theme.soft(), 0xFFFFFFFF, on)));
            String desc = textRenderer.trimToWidth(p.blurb, (int) ((cw - 76) / 0.75f));
            small(ctx, desc, cx + 10, y + 21, 0.75f, A(Theme.dim()));
            int[] dots = Theme.preview(p);
            for (int i = 0; i < 3; i++) {
                int dx = cx + cw - 52 + i * 14;
                Gfx.roundFill(ctx, dx, y + 13, 12, 12, 6, A(0xFF000000 | dots[i]));
                Gfx.roundBorder(ctx, dx, y + 13, 12, 12, 6, A(0x66FFFFFF));
            }
            y += CARD_H + GAP;
        }
        ctx.disableScissor();
    }

    private void drawPane(DrawContext ctx, int mx, int my) {
        int sx = setX(), sw = setR() - sx;
        Gfx.roundFill(ctx, sx, top(), sw, bot() - top(), 9, A(0x55000000));
        if (page == Page.MODULE && selected == null) {
            text(ctx, "Select a module", sx + 16, top() + 16, A(Theme.dim()));
            return;
        }
        ctx.enableScissor(sx, top() + 1, sx + sw, bot() - 1);
        int ix = sx + 12, iw = sw - 24;

        int hy = top() + 8 - setScroll;
        small(ctx, paneTitle(), ix, hy + 2, 1.5f, A(0xFFFFFFFF));
        int ly = hy + 28;
        for (OrderedText line : textRenderer.wrapLines(Text.literal(paneDesc()), iw - 4)) {
            ctx.drawText(textRenderer, line, ix, ly, A(Theme.dim()), false);
            ly += 10;
        }
        if (page == Page.THEMES) {
            for (int x = 0; x < iw; x += 2) {
                int col = 0xFF000000 | ColorUtil.lerp(Theme.a1(), Theme.a2(), x / (float) iw);
                ctx.fill(ix + x, ly + 4, Math.min(ix + x + 2, ix + iw), ly + 10, A(col));
            }
        }
        if (page == Page.MODULE) {
            boolean hv = mx >= ix + iw - 48 && mx < ix + iw && my >= hy + 2 && my < hy + 18;
            chip(ctx, "< Back", ix + iw - 48, hy + 2, 48, false, hv);
        }

        for (Row r : rows()) {
            if (r.y() + r.h() < top() || r.y() > bot()) continue;
            switch (r.kind()) {
                case ENABLE -> {
                    float t = an(selected.name + "#big", selected.enabled);
                    int c1 = Gfx.lerpArgb(A(0x44FFFFFF), ac(0.1f, 0.9f), t);
                    int c2 = Gfx.lerpArgb(A(0x33FFFFFF), ac(0.9f, 0.9f), t);
                    Gfx.roundGradientV(ctx, ix, r.y(), iw, r.h(), 8, c1, c2);
                    String s = selected.enabled ? "ENABLED" : "DISABLED";
                    text(ctx, s, ix + iw / 2 - textRenderer.getWidth(s) / 2, r.y() + 10, A(0xFFFFFFFF));
                }
                case BIND -> {
                    card(ctx, ix, r, iw);
                    text(ctx, "Shortcut", ix + 10, r.y() + 9, A(Theme.text()));
                    String k = binding ? "press a key..." : keyName(selected.key);
                    int w = textRenderer.getWidth(k) + 14;
                    Gfx.roundFill(ctx, ix + iw - w - 6, r.y() + 4, w, 18, 5, binding ? ac(0.2f, 0.35f) : A(0x44000000));
                    text(ctx, k, ix + iw - w - 6 + 7, r.y() + 9, binding ? A(0xFFFFFFFF) : A(Theme.dim()));
                }
                case DISABLE_ALL -> {
                    card(ctx, ix, r, iw);
                    text(ctx, "Disable all modules", ix + 10, r.y() + 9, A(Theme.text()));
                    int w = textRenderer.getWidth("Disable") + 14;
                    boolean hv = mx >= ix + iw - w - 8 && mx < ix + iw - 8 && my >= r.y() + 5 && my < r.y() + 21;
                    chip(ctx, "Disable", ix + iw - w - 8, r.y() + 5, w, false, hv);
                }
                case SETTING -> drawSetting(ctx, r, ix, iw, mx, my);
            }
        }
        ctx.disableScissor();
    }

    private void card(DrawContext ctx, int ix, Row r, int iw) {
        Gfx.roundFill(ctx, ix, r.y(), iw, r.h(), 8, A(0x33FFFFFF));
    }

    private void drawSetting(DrawContext ctx, Row r, int ix, int iw, int mx, int my) {
        Setting<?> s = r.s();
        int y = r.y();
        card(ctx, ix, r, iw);
        text(ctx, s.name, ix + 10, y + 9, A(Theme.text()));
        if (s instanceof Setting.Bool b) {
            sw(ctx, ix + iw - 36, y + 6, s, b.on());
        } else if (s instanceof Setting.Choice<?> c) {
            Object[] opts = c.options();
            for (int i = 0; i < opts.length; i++) {
                boolean on = opts[i] == c.value;
                int oy = y + 22 + i * 15;
                float t = an(c.name + "#" + System.identityHashCode(c) + i, on);
                if (t > 0.02f) Gfx.roundFill(ctx, ix + 8, oy, iw - 16, 13, 5, ac(i / 4f, 0.30f * t));
                text(ctx, String.valueOf(opts[i]), ix + 16, oy + 3, A(on ? 0xFFFFFFFF : Theme.dim()));
                if (on) Gfx.roundFill(ctx, ix + iw - 20, oy + 4, 5, 5, 2, ac(i / 4f, 1f));
            }
        } else if (s instanceof Setting.Num n) {
            String v = n.step >= 1 ? String.valueOf(n.i()) : String.format("%.2f", n.d()).replaceAll("0+$", "").replaceAll("\\.$", "");
            text(ctx, v, ix + iw - 10 - textRenderer.getWidth(v), y + 9, A(0xFFFFFFFF));
            int x0 = ix + 10, w = iw - 20;
            Gfx.roundFill(ctx, x0, y + 25, w, 4, 2, A(Theme.track()));
            int fw = Math.max(4, (int) (w * n.fraction()));
            Gfx.roundFill(ctx, x0, y + 25, fw, 4, 2, ac(0.2f, 1f));
            Gfx.roundFill(ctx, x0 + fw - 4, y + 23, 8, 8, 4, A(0xFFFFFFFF));
        } else if (s instanceof Setting.BlockSet bs) {
            drawBlockSet(ctx, bs, y, ix, iw, mx, my);
        } else if (s instanceof Setting.Picks p) {
            int cols = pickCols(p, iw);
            int hover = -1;
            for (int i = 0; i < p.icons.length; i++) {
                int cx = ix + 8 + (i % cols) * 24, cy = y + 22 + (i / cols) * 24;
                boolean sel = p.on(i);
                boolean hv = mx >= cx && mx < cx + 22 && my >= cy && my < cy + 22;
                if (hv) hover = i;
                float t = an("pick#" + System.identityHashCode(p) + "#" + i, sel);
                int bg = Gfx.lerpArgb(A(0x22FFFFFF), ac(i / 8f, 0.35f), t);
                if (hv) bg = Gfx.lerpArgb(bg, A(0x44FFFFFF), 0.5f);
                Gfx.roundFill(ctx, cx, cy, 22, 22, 6, bg);
                if (t > 0.02f) Gfx.roundBorder(ctx, cx, cy, 22, 22, 6, ac(i / 8f, 0.9f * t));
                ctx.drawItem(new ItemStack(p.icons[i]), cx + 3, cy + 3);
            }
            String hl = hover >= 0 ? p.labels[hover] : p.count() + " selected";
            text(ctx, hl, ix + iw - 10 - textRenderer.getWidth(hl), y + 9, A(hover >= 0 ? Theme.text() : Theme.dim()));
        } else if (s instanceof Setting.Str t) {
            int bx = ix + 8, by = y + 22, bw = iw - 16, bh = 16;
            boolean f = textFocus == t;
            float fa = an("txt#" + System.identityHashCode(t), f);
            Gfx.roundFill(ctx, bx, by, bw, bh, 5, A(0x66000000));
            Gfx.roundBorder(ctx, bx, by, bw, bh, 5, Gfx.lerpArgb(A(0x33FFFFFF), ac(0.6f, 0.9f), fa));
            boolean empty = t.value.isEmpty();
            String shown;
            if (empty) {
                shown = f ? "" : textRenderer.trimToWidth(t.hint, bw - 12);
            } else {
                shown = t.value;
                while (!shown.isEmpty() && textRenderer.getWidth(shown) > bw - 14) shown = shown.substring(1);
            }
            text(ctx, shown, bx + 6, by + 4, A(empty ? Theme.faint() : Theme.text()));
            if (f && (int) (time * 2) % 2 == 0) {
                int cx = bx + 6 + textRenderer.getWidth(shown);
                ctx.fill(cx, by + 3, cx + 1, by + 13, A(0xFFFFFFFF));
            }
        } else if (s instanceof Setting.Color c) {
            Gfx.roundFill(ctx, ix + iw - 30, y + 6, 20, 12, 4, A(0xFF000000 | c.rgb()));
            Gfx.roundBorder(ctx, ix + iw - 30, y + 6, 20, 12, 4, A(0x66FFFFFF));
            int x0 = ix + 10, w = (iw - 20) / 3;
            int[] cols = {0xFFE05555, 0xFF55C46A, 0xFF5587E0};
            for (int i = 0; i < 3; i++) {
                int sx = x0 + i * w;
                Gfx.roundFill(ctx, sx, y + 28, w - 6, 4, 2, A(Theme.track()));
                int fw = Math.max(4, (int) ((w - 6) * (c.ch(i) / 255.0)));
                Gfx.roundFill(ctx, sx, y + 28, fw, 4, 2, A(cols[i]));
                Gfx.roundFill(ctx, sx + fw - 4, y + 26, 8, 8, 4, A(0xFFFFFFFF));
            }
        }
    }

    private void chip(DrawContext ctx, String label, int x, int y, int w, boolean active, boolean hv) {
        int bg = Gfx.lerpArgb(A(0x22FFFFFF), ac(0.4f, 0.35f), active ? 1f : 0f);
        if (hv) bg = Gfx.lerpArgb(bg, A(0x44FFFFFF), 0.5f);
        Gfx.roundFill(ctx, x, y, w, 16, 5, bg);
        text(ctx, label, x + (w - textRenderer.getWidth(label)) / 2, y + 4, A(active ? 0xFFFFFFFF : Theme.soft()));
    }

    private void drawBlockSet(DrawContext ctx, Setting.BlockSet bs, int y, int ix, int iw, int mx, int my) {
        java.util.List<net.minecraft.block.Block> list = bs.list();
        int cols = bsCols(iw);
        int perPage = cols * Setting.BlockSet.ROWS;
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        bs.page = Math.max(0, Math.min(pages - 1, bs.page));

        // search box
        int bx = ix + 8, by = y + 22, bw = iw - 16;
        boolean f = textFocus == bs.search;
        float fa = an("bsearch#" + System.identityHashCode(bs), f);
        Gfx.roundFill(ctx, bx, by, bw, 16, 5, A(0x66000000));
        Gfx.roundBorder(ctx, bx, by, bw, 16, 5, Gfx.lerpArgb(A(0x33FFFFFF), ac(0.6f, 0.9f), fa));
        boolean empty = bs.search.value.isEmpty();
        String shown = empty ? (f ? "" : bs.search.hint) : bs.search.value;
        while (!shown.isEmpty() && textRenderer.getWidth(shown) > bw - 14) shown = empty ? shown.substring(0, shown.length() - 1) : shown.substring(1);
        text(ctx, shown, bx + 6, by + 4, A(empty ? Theme.faint() : Theme.text()));
        if (f && (int) (time * 2) % 2 == 0) {
            int cx = bx + 6 + textRenderer.getWidth(shown);
            ctx.fill(cx, by + 3, cx + 1, by + 13, A(0xFFFFFFFF));
        }

        // grid
        int gy = y + 44;
        int hover = -1;
        int start = bs.page * perPage;
        for (int k = 0; k < perPage && start + k < list.size(); k++) {
            net.minecraft.block.Block b = list.get(start + k);
            int cx = ix + 8 + (k % cols) * 24, cy = gy + (k / cols) * 24;
            boolean sel = bs.has(b);
            boolean hv = mx >= cx && mx < cx + 22 && my >= cy && my < cy + 22;
            if (hv) hover = start + k;
            int bg = sel ? ac(k / 8f, 0.35f) : A(0x22FFFFFF);
            if (hv) bg = Gfx.lerpArgb(bg, A(0x44FFFFFF), 0.5f);
            Gfx.roundFill(ctx, cx, cy, 22, 22, 6, bg);
            if (sel) Gfx.roundBorder(ctx, cx, cy, 22, 22, 6, ac(k / 8f, 0.9f));
            ctx.drawItem(new ItemStack(b), cx + 3, cy + 3);
        }
        if (list.isEmpty()) text(ctx, "No blocks match", ix + 12, gy + 6, A(Theme.dim()));

        // header: hovered name or selection count
        String hl = hover >= 0 ? bs.nameOf(list.get(hover)) : bs.value.size() + " selected / " + Setting.BlockSet.totalBlocks();
        text(ctx, textRenderer.trimToWidth(hl, iw - 20 - textRenderer.getWidth(bs.name)), ix + iw - 10 - textRenderer.getWidth(textRenderer.trimToWidth(hl, iw - 20 - textRenderer.getWidth(bs.name))), y + 9, A(hover >= 0 ? Theme.text() : Theme.dim()));

        // pager
        int py = gy + Setting.BlockSet.ROWS * 24 + 4;
        chip(ctx, "< Prev", ix + 8, py, 40, false, mx >= ix + 8 && mx < ix + 48 && my >= py && my < py + 16);
        chip(ctx, "Next >", ix + 52, py, 40, false, mx >= ix + 52 && mx < ix + 92 && my >= py && my < py + 16);
        text(ctx, (bs.page + 1) + " / " + pages, ix + 98, py + 4, A(Theme.dim()));
        String so = "Selected only";
        int sw2 = textRenderer.getWidth(so) + 14;
        int sx2 = ix + iw - 8 - sw2;
        chip(ctx, so, sx2, py, sw2, bs.selectedOnly, mx >= sx2 && mx < sx2 + sw2 && my >= py && my < py + 16);
    }

    private void clickBlockSet(Setting.BlockSet bs, int y, int ix, int iw, double mx, double my) {
        java.util.List<net.minecraft.block.Block> list = bs.list();
        int cols = bsCols(iw);
        int perPage = cols * Setting.BlockSet.ROWS;
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);

        // search box
        if (mx >= ix + 8 && mx < ix + iw - 8 && my >= y + 22 && my < y + 38) {
            textFocus = bs.search;
            return;
        }
        int gy = y + 44;
        int py = gy + Setting.BlockSet.ROWS * 24 + 4;
        if (my >= py && my < py + 16) {
            if (mx >= ix + 8 && mx < ix + 48) bs.page = Math.max(0, bs.page - 1);
            else if (mx >= ix + 52 && mx < ix + 92) bs.page = Math.min(pages - 1, bs.page + 1);
            else {
                int sw2 = textRenderer.getWidth("Selected only") + 14;
                if (mx >= ix + iw - 8 - sw2 && mx < ix + iw - 8) bs.selectedOnly = !bs.selectedOnly;
            }
            return;
        }
        int col = (int) Math.floor((mx - (ix + 8)) / 24.0);
        int row = (int) Math.floor((my - gy) / 24.0);
        if (col >= 0 && col < cols && row >= 0 && row < Setting.BlockSet.ROWS) {
            int idx = bs.page * perPage + row * cols + col;
            if (idx < list.size()) bs.toggle(list.get(idx));
        }
    }

    private String keyName(int key) {
        if (key < 0) return "None";
        try {
            return InputUtil.fromKeyCode(key, 0).getLocalizedText().getString();
        } catch (Exception e) {
            return "Key " + key;
        }
    }

    // ------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        layout();
        textFocus = null;

        // window buttons
        int cy = wy + 15;
        if (my >= cy && my < cy + 18) {
            if (mx >= closeX() && mx < closeX() + 18) {
                close();
                return true;
            }
            if (mx >= minX() && mx < minX() + 18) {
                minimized = !minimized;
                searchFocus = false;
                return true;
            }
        }

        // search box
        int sx = searchX(), sy = wy + 14;
        boolean inSearch = mx >= sx && mx < sx + 150 && my >= sy && my < sy + 20;
        searchFocus = inSearch && !minimized;
        if (inSearch) return true;
        if (minimized) return super.mouseClicked(mx, my, button);

        // sidebar
        int tx = wx + 8, tw = SIDE - 16;
        if (mx >= tx && mx < tx + tw && my >= top()) {
            List<String> labels = sideLabels();
            for (int i = 0; i < labels.size(); i++) {
                int ty = top() + 8 + i * 30;
                if (my >= ty && my < ty + 26) {
                    goPanel(labels.get(i));
                    return true;
                }
            }
            for (int k = 0; k < 2; k++) {
                int by = bottomY(k);
                if (my >= by && my < by + 22) {
                    openPage(k == 0 ? Page.SETTINGS : Page.THEMES);
                    return true;
                }
            }
            return true;
        }

        if (my < top() || my >= bot()) return super.mouseClicked(mx, my, button);

        // module grid
        if (page == Page.MODULES) return clickPanels(mx, my, button);

        // theme list
        if (page == Page.THEMES) {
            int lx = listX() + 2, lw = LIST_W - 4;
            if (mx >= lx && mx < lx + lw) {
                int y = top() + 6 - listScroll;
                int cx = lx + 6, cw = lw - 12;
                for (Theme.Preset p : Theme.Preset.values()) {
                    if (my >= y && my < y + CARD_H && mx >= cx && mx < cx + cw) {
                        Theme.PRESET.set(p.ordinal());
                        setScroll = 0;
                        return true;
                    }
                    y += CARD_H + GAP;
                }
                return true;
            }
        }

        // settings pane (module page, themes, settings)
        if (mx >= setX() && mx < setR()) {
            int ix = setX() + 12, iw = setR() - setX() - 24;
            if (page == Page.MODULE) {
                int hy = top() + 8 - setScroll;
                if (mx >= ix + iw - 48 && mx < ix + iw && my >= hy + 2 && my < hy + 18) {
                    page = Page.MODULES;
                    binding = false;
                    return true;
                }
            }
            for (Row r : rows()) {
                if (my < r.y() || my >= r.y() + r.h()) continue;
                if (mx < ix || mx > ix + iw) break;
                switch (r.kind()) {
                    case ENABLE -> selected.toggle();
                    case BIND -> binding = !binding;
                    case DISABLE_ALL -> disableAll();
                    case SETTING -> clickSetting(r, mx, my, button, ix, iw);
                }
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    private boolean clickPanels(double mx, double my, int button) {
        int cx0 = contentX(), cw = contentR() - cx0, colW = (cw - PGAP) / 2;
        int oy = top() + 6 - Math.round(scroll);
        for (Panel p : panels()) {
            int px = p.col < 0 ? cx0 : cx0 + p.col * (colW + PGAP);
            int pw = p.col < 0 ? cw : colW;
            for (int i = 0; i < p.mods.size(); i++) {
                int ry = oy + p.y + PANEL_HEAD + i * ROW_H;
                if (mx >= px + 4 && mx < px + pw - 4 && my >= ry && my < ry + ROW_H) {
                    Module m = p.mods.get(i);
                    if (button == 1 || mx >= px + pw - 44) {
                        m.toggle();
                    } else {
                        selected = m;
                        page = Page.MODULE;
                        setScroll = 0;
                        binding = false;
                        sideSel = m.category.label;
                    }
                    return true;
                }
            }
        }
        return true;
    }

    private void clickSetting(Row r, double mx, double my, int button, int ix, int iw) {
        Setting<?> s = r.s();
        if (s instanceof Setting.Bool b) {
            b.value = !b.value;
        } else if (s instanceof Setting.Choice<?> c) {
            int idx = (int) Math.floor((my - (r.y() + 22)) / 15.0);
            if (idx >= 0 && idx < c.options().length) c.set(idx);
            else c.next(button == 1 ? -1 : 1);
        } else if (s instanceof Setting.Num n) {
            dragNum = n;
            dragX0 = ix + 10;
            dragW = iw - 20;
            n.setFraction((mx - dragX0) / dragW);
        } else if (s instanceof Setting.BlockSet bs) {
            clickBlockSet(bs, r.y(), ix, iw, mx, my);
        } else if (s instanceof Setting.Picks p) {
            int cols = pickCols(p, iw);
            int col = (int) Math.floor((mx - (ix + 8)) / 24.0);
            int row = (int) Math.floor((my - (r.y() + 22)) / 24.0);
            if (col >= 0 && col < cols && row >= 0) {
                int i = row * cols + col;
                if (i < p.icons.length) p.toggle(i);
            }
        } else if (s instanceof Setting.Str t) {
            textFocus = t;
        } else if (s instanceof Setting.Color c) {
            int w = (iw - 20) / 3;
            int ch = (int) ((mx - (ix + 10)) / w);
            if (ch < 0 || ch > 2) return;
            dragColor = c;
            dragCh = ch;
            dragX0 = ix + 10 + ch * w;
            dragW = w - 6;
            c.setCh(ch, (int) Math.round(255.0 * (mx - dragX0) / dragW));
        }
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragNum != null) {
            dragNum.setFraction((mx - dragX0) / dragW);
            return true;
        }
        if (dragColor != null) {
            dragColor.setCh(dragCh, (int) Math.round(255.0 * (mx - dragX0) / dragW));
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragNum = null;
        dragColor = null;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double hAmount, double vAmount) {
        layout();
        if (minimized) return true;
        if (page == Page.MODULES) {
            scrollTarget = Math.max(0, Math.min(maxScroll(), scrollTarget - (int) (vAmount * 28)));
        } else if (page == Page.THEMES && mx >= listX() && mx < listX() + LIST_W) {
            int max = Math.max(0, Theme.Preset.values().length * (CARD_H + GAP) + 12 - (bot() - top()));
            listScroll = Math.max(0, Math.min(max, listScroll - (int) (vAmount * 20)));
        } else {
            int max = Math.max(0, contentHeight() + 12 - (bot() - top()));
            setScroll = Math.max(0, Math.min(max, setScroll - (int) (vAmount * 20)));
        }
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (textFocus != null) {
            if (chr >= 32 && chr != 127 && textFocus.value.length() < textFocus.maxLen) textFocus.value += chr;
            return true;
        }
        if (searchFocus && chr >= 32 && chr != 127 && search.length() < 24) {
            search += chr;
            listScroll = 0;
            page = Page.MODULES;
            scrollTarget = 0;
            scroll = 0;
            return true;
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding && selected != null) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                binding = false;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE || keyCode == GLFW.GLFW_KEY_DELETE) {
                selected.key = -1;
                binding = false;
            } else {
                selected.key = keyCode;
                binding = false;
            }
            return true;
        }
        if (textFocus != null) {
            Setting.Str t = textFocus;
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                textFocus = null;
            } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!t.value.isEmpty()) t.value = t.value.substring(0, t.value.length() - 1);
            } else if (keyCode == GLFW.GLFW_KEY_V) {
                long h = MinecraftClient.getInstance().getWindow().getHandle();
                boolean ctrl = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
                if (ctrl) {
                    String clip = GLFW.glfwGetClipboardString(h);
                    if (clip != null) {
                        String add = clip.replaceAll("[\\r\\n]+", " ");
                        String nv = t.value + add;
                        t.value = nv.length() > t.maxLen ? nv.substring(0, t.maxLen) : nv;
                    }
                }
            }
            return true;
        }
        if (searchFocus) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchFocus = false;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) search = search.substring(0, search.length() - 1);
                return true;
            }
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && page != Page.MODULES) {
            page = Page.MODULES;
            setScroll = 0;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        Config.save();
        super.close();
    }
}
