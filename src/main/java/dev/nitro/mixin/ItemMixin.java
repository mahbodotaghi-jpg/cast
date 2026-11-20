package dev.nitro.mixin;

import dev.nitro.modules.CustomAccessories;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ensures the local item render state sees a glint on eligible tools. */
@Mixin(Item.class)
public abstract class ItemMixin {
    @Inject(method = "hasGlint", at = @At("HEAD"), cancellable = true)
    private void nitro$addCosmeticToolGlint(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (CustomAccessories.shouldGiveGlint(stack)) cir.setReturnValue(true);
    }
}
