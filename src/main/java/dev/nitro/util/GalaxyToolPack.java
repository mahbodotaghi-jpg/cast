package dev.nitro.util;

import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.resource.ResourcePackManager;

/** Enables/disables NITRO's built-in galaxy item textures on this client only. */
public final class GalaxyToolPack {
    private static final String PACK_MARKER = "galaxy_tools";
    private static long lastScanMs;
    private static String profileId;

    private GalaxyToolPack() {}

    public static void register() {
        FabricLoader.getInstance().getModContainer("nitro").ifPresent(container ->
                ResourceManagerHelper.registerBuiltinResourcePack(
                        Identifier.of("nitro", "galaxy_tools"), container,
                        Text.literal("NITRO Galaxy Tools"), ResourcePackActivationType.NORMAL));
    }

    public static void setEnabled(boolean enabled) {
        MinecraftClient client = MinecraftClient.getInstance();
        Runnable action = () -> apply(client, enabled);
        if (client.isOnThread()) action.run(); else client.execute(action);
    }

    private static void apply(MinecraftClient client, boolean enabled) {
        try {
            ResourcePackManager manager = client.getResourcePackManager();
            if (profileId == null || !manager.hasProfile(profileId)) {
                long now = System.currentTimeMillis();
                if (now - lastScanMs < 1500L) return;
                lastScanMs = now;
                manager.scanPacks();
                profileId = manager.getIds().stream()
                        .filter(id -> id.contains(PACK_MARKER))
                        .findFirst().orElse(null);
            }
            if (profileId == null) return;
            boolean current = manager.getEnabledIds().contains(profileId);
            if (current == enabled) return;
            boolean changed = enabled ? manager.enable(profileId) : manager.disable(profileId);
            if (changed) client.reloadResources();
        } catch (Throwable t) {
            System.err.println("[NITRO] Could not toggle Galaxy Tools resource pack: " + t.getMessage());
        }
    }
}
