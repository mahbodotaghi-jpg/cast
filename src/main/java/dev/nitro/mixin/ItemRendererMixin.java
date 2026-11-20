package dev.nitro.mixin;

import dev.nitro.modules.CustomAccessories;
import dev.nitro.modules.CustomGlint;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Redirect(method = "getItemGlintConsumer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/VertexConsumerProvider;getBuffer(Lnet/minecraft/client/render/RenderLayer;)Lnet/minecraft/client/render/VertexConsumer;"),
            require = 0)
    private static VertexConsumer nitro$tintGlintBuffer(VertexConsumerProvider provider, RenderLayer layer) {
        VertexConsumer original = provider.getBuffer(layer);
        if (!CustomGlint.active() && !CustomAccessories.active()) return original;
        if (layer != RenderLayer.getGlint() && layer != RenderLayer.getGlintTranslucent()) return original;
        int tint = CustomAccessories.active() ? CustomAccessories.tintRgb() : CustomGlint.tintRgb();
        return new TintingConsumer(original, tint);
    }

    private static final class TintingConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int rgb;
        private TintingConsumer(VertexConsumer delegate, int rgb) { this.delegate = delegate; this.rgb = rgb; }
        @Override public VertexConsumer vertex(float x, float y, float z) { delegate.vertex(x, y, z); return this; }
        @Override public VertexConsumer color(int r, int g, int b, int a) {
            delegate.color((r * ((rgb >> 16) & 255)) / 255,
                    (g * ((rgb >> 8) & 255)) / 255,
                    (b * (rgb & 255)) / 255, a);
            return this;
        }
        @Override public VertexConsumer texture(float u, float v) { delegate.texture(u, v); return this; }
        @Override public VertexConsumer overlay(int u, int v) { delegate.overlay(u, v); return this; }
        @Override public VertexConsumer light(int u, int v) { delegate.light(u, v); return this; }
        @Override public VertexConsumer normal(float x, float y, float z) { delegate.normal(x, y, z); return this; }
    }
}
