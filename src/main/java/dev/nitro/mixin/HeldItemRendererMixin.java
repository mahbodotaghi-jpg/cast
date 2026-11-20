package dev.nitro.mixin;

import dev.nitro.modules.CustomAnimation;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Applies the selected visual curve at the swing-transform method itself. */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @ModifyVariable(
            method = "applySwingOffset",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            require = 0
    )
    private float nitro$customSwingProgress(float vanillaProgress) {
        return CustomAnimation.mapSwingProgress(vanillaProgress);
    }
}
