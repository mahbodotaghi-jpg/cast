package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;

/** Lets the local camera look around while the player's body yaw and pitch stay put. */
public final class FreeLook extends Module {
    public static FreeLook INSTANCE;
    public float yaw, pitch;
    private boolean ready;
    private final Setting.Num sensitivity = new Setting.Num("Look sensitivity", 1.0, 0.2, 2.0, 0.1);

    public FreeLook() {
        super("Free Look", "Look around without rotating your player model.", Category.MOVEMENT);
        add(sensitivity);
        INSTANCE = this;
    }

    public static boolean active() {
        return INSTANCE != null && INSTANCE.enabled && INSTANCE.ready && !Freecam.active();
    }

    @Override public void onEnable() {
        if (mc.player == null || mc.world == null) { enabled = false; return; }
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        ready = true;
    }

    @Override public void onDisable() { ready = false; }

    @Override public void onWorldChanged() {
        if (enabled) setEnabled(false);
    }

    public void look(double dx, double dy) {
        float s = sensitivity.f() * 0.15f;
        yaw += (float)dx * s;
        pitch = Math.max(-90f, Math.min(90f, pitch + (float)dy * s));
    }
}
