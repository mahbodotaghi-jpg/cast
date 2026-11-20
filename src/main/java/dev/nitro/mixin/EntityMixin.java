package dev.nitro.mixin;

import dev.nitro.modules.Freecam;
import dev.nitro.modules.FreeLook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    /** While freecam is active, mouse movement turns the camera instead of the player. */
    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void nitro$freecamLook(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if ((Object) this != MinecraftClient.getInstance().player) return;
        if (Freecam.active()) {
            Freecam.INSTANCE.look(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        } else if (FreeLook.active()) {
            FreeLook.INSTANCE.look(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }
}
