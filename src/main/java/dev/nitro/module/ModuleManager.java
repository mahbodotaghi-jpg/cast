package dev.nitro.module;

import dev.nitro.Config;
import dev.nitro.util.AttackHooks;
import dev.nitro.modules.AnchorMacro;
import dev.nitro.modules.AutoHitCrystal;
import dev.nitro.modules.AutoInvTotem;
import dev.nitro.modules.AutoWindCharge;
import dev.nitro.modules.BreachSwap;
import dev.nitro.modules.ElytraSwap;
import dev.nitro.modules.FallReset;
import dev.nitro.modules.MaceSwap;
import dev.nitro.modules.ShieldBreaker;
import dev.nitro.modules.StunSlam;
import dev.nitro.modules.SmashAim;
import dev.nitro.modules.AutoCrystal;
import dev.nitro.modules.AutoTotem;
import dev.nitro.modules.HoverTotem;
import dev.nitro.modules.BaseFinder;
import dev.nitro.modules.BlockEsp;
import dev.nitro.modules.SeedOreEsp;
import dev.nitro.modules.ArmorHud;
import dev.nitro.modules.Aura;
import dev.nitro.modules.WeatherControl;
import dev.nitro.modules.CustomSky;
import dev.nitro.modules.CustomAnimation;
import dev.nitro.modules.CustomFov;
import dev.nitro.modules.FreeLook;
import dev.nitro.modules.Freecam;
import dev.nitro.modules.Fullbright;
import dev.nitro.modules.NoRender;
import dev.nitro.modules.Keystrokes;
import dev.nitro.modules.LightDetector;
import dev.nitro.modules.MotionBlur;
import dev.nitro.modules.PlayerEsp;
import dev.nitro.modules.SpawnerAlert;
import dev.nitro.modules.SpotifyHud;
import dev.nitro.modules.StorageEsp;
import dev.nitro.modules.TargetHud;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.client.world.ClientWorld;

import java.util.ArrayList;
import java.util.List;

public final class ModuleManager {
    public static final ModuleManager INSTANCE = new ModuleManager();

    public final List<Module> modules = new ArrayList<>();
    private ClientWorld lastWorld;

    private ModuleManager() {}

    public void init() {
        modules.add(new LightDetector());
        modules.add(new BaseFinder());
        modules.add(new SpawnerAlert());
        modules.add(new Freecam());
        modules.add(new StorageEsp());
        modules.add(new SeedOreEsp());
        modules.add(new BlockEsp());
        modules.add(new PlayerEsp());
        modules.add(new Fullbright());
        modules.add(new NoRender());
        modules.add(new CustomSky());
        modules.add(new CustomAnimation());
        modules.add(new CustomFov());
        modules.add(new Aura());
        modules.add(new FreeLook());
        modules.add(new AutoTotem());
        modules.add(new HoverTotem());
        modules.add(new AutoCrystal());
        modules.add(new AnchorMacro());
        modules.add(new AutoInvTotem());
        modules.add(new AutoHitCrystal());
        modules.add(new StunSlam()); // before Shield Breaker: it needs swap priority for the axe + mace combo
        modules.add(new ShieldBreaker()); // swap priority: Shield Breaker, then Mace Swap, then Breach Swap
        modules.add(new MaceSwap());
        modules.add(new BreachSwap());
        modules.add(new AutoWindCharge());
        modules.add(new SmashAim());
        modules.add(new FallReset());
        modules.add(new ElytraSwap());
        modules.add(new WeatherControl());
        modules.add(new MotionBlur()); // must stay before the HUD modules so their panels are drawn after the blur
        modules.add(new TargetHud());
        modules.add(new SpotifyHud());
        modules.add(new Keystrokes());
        modules.add(new ArmorHud());
        Config.load();
    }

    public void tick(MinecraftClient mc) {
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            AttackHooks.reset();
            for (Module m : modules) m.onWorldChanged();
        }
        if (mc.world == null || mc.player == null) return;
        AttackHooks.tick();

        long handle = mc.getWindow().getHandle();
        for (Module m : modules) {
            if (m.key >= 0 && mc.currentScreen == null) {
                boolean down = InputUtil.isKeyPressed(handle, m.key);
                if (down && !m.keyWasDown) m.toggle();
                m.keyWasDown = down;
            } else {
                m.keyWasDown = false;
            }
            if (m.enabled) m.onTick();
        }
    }

    public void renderWorld(WorldRenderContext ctx) {
        for (Module m : modules) if (m.enabled) m.renderSkyLate(ctx);
        for (Module m : modules) if (m.enabled) m.renderWorld(ctx);
    }

    public void renderSky(WorldRenderContext ctx) {
        for (Module m : modules) if (m.enabled) m.renderSky(ctx);
    }

    public void renderHud(DrawContext ctx) {
        for (Module m : modules) if (m.enabled) m.renderHud(ctx);
    }
}
