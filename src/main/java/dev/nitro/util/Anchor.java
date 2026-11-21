package dev.nitro.util;

import dev.nitro.settings.Setting;

/** Screen corner a HUD panel is pinned to. */
public enum Anchor {
    TOP_LEFT("Top left"), TOP_RIGHT("Top right"), BOTTOM_LEFT("Bottom left"), BOTTOM_RIGHT("Bottom right");

    private final String label;

    Anchor(String l) {
        label = l;
    }

    public int x(int screenW, int w, int margin) {
        return (this == TOP_LEFT || this == BOTTOM_LEFT) ? margin : screenW - w - margin;
    }

    public int y(int screenH, int h, int margin) {
        return (this == TOP_LEFT || this == TOP_RIGHT) ? margin : screenH - h - margin;
    }

    /** Offset from the pinned corner that puts a box of this size with its top-left at (x, y). */
    public int offX(int screenW, int w, int x) {
        return (this == TOP_LEFT || this == BOTTOM_LEFT) ? x : screenW - w - x;
    }

    public int offY(int screenH, int h, int y) {
        return (this == TOP_LEFT || this == TOP_RIGHT) ? y : screenH - h - y;
    }

    /** The corner closest to the centre of a box at (x, y) with size w x h. */
    public static Anchor nearest(int x, int y, int w, int h, int screenW, int screenH) {
        boolean left = x + w / 2 < screenW / 2;
        boolean top = y + h / 2 < screenH / 2;
        return top ? (left ? TOP_LEFT : TOP_RIGHT) : (left ? BOTTOM_LEFT : BOTTOM_RIGHT);
    }

    /** Moves a corner-pinned HUD box so its top-left lands on (x, y): picks the nearest corner and stores the offsets. */
    public static void place(Setting.Choice<Anchor> anchor, Setting.Num ox, Setting.Num oy,
                             int x, int y, int w, int h, int screenW, int screenH) {
        Anchor a = nearest(x, y, w, h, screenW, screenH);
        anchor.value = a;
        ox.set(a.offX(screenW, w, x));
        oy.set(a.offY(screenH, h, y));
    }

    @Override
    public String toString() {
        return label;
    }
}
