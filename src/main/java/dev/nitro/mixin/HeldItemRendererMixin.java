package dev.nitro.mixin;

import dev.nitro.modules.CustomAnimation;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Applies a local-only easing curve to the first-person hand/item swing. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @ModifyVariable(
            method = "renderFirstPersonItem",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 2
    )
    private float nitro$customSwingProgress(float vanillaProgress) {
        return CustomAnimation.mapSwingProgress(vanillaProgress);
    }
}
