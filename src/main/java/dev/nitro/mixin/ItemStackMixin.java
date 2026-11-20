package dev.nitro.mixin;

import dev.nitro.modules.CustomAccessories;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Local-only fallback for item stacks rendered through ItemStack.hasGlint(). */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "hasGlint", at = @At("HEAD"), cancellable = true)
    private void nitro$addCosmeticToolGlint(CallbackInfoReturnable<Boolean> cir) {
        if (CustomAccessories.shouldGiveGlint((ItemStack) (Object) this)) cir.setReturnValue(true);
    }
}
