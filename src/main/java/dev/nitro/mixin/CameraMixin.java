package dev.nitro.mixin;

import dev.nitro.modules.Freecam;
import dev.nitro.modules.FreeLook;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Unique
    private boolean nitro$lookSwapped;
    @Unique
    private float nitro$yaw, nitro$pitch, nitro$prevYaw, nitro$prevPitch;

    /**
     * Free Look: while the camera is being positioned, the player's view angles are swapped for the free look angles.
     * The camera then orbits correctly in third person (not just rotates in place), and the real angles are put back
     * right after, so your body, movement and aim are untouched.
     */
    @Inject(method = "update", at = @At("HEAD"))
    private void nitro$freeLookBegin(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
                                     float tickDelta, CallbackInfo ci) {
        nitro$lookSwapped = false;
        if (focusedEntity == null || Freecam.active() || !FreeLook.active()) return;
        if (focusedEntity != MinecraftClient.getInstance().player) return;
        FreeLook f = FreeLook.INSTANCE;
        nitro$yaw = focusedEntity.getYaw();
        nitro$pitch = focusedEntity.getPitch();
        nitro$prevYaw = focusedEntity.prevYaw;
        nitro$prevPitch = focusedEntity.prevPitch;
        focusedEntity.setYaw(f.yaw);
        focusedEntity.setPitch(f.pitch);
        focusedEntity.prevYaw = f.yaw;
        focusedEntity.prevPitch = f.pitch;
        nitro$lookSwapped = true;
    }

    @Inject(method = "update", at = @At("RETURN"))
    private void nitro$freecamPose(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView,
                                   float tickDelta, CallbackInfo ci) {
        if (Freecam.active()) {
            Freecam f = Freecam.INSTANCE;
            setPos(f.x(tickDelta), f.y(tickDelta), f.z(tickDelta));
            setRotation(f.yaw, f.pitch);
        }
        if (nitro$lookSwapped && focusedEntity != null) {
            focusedEntity.setYaw(nitro$yaw);
            focusedEntity.setPitch(nitro$pitch);
            focusedEntity.prevYaw = nitro$prevYaw;
            focusedEntity.prevPitch = nitro$prevPitch;
            nitro$lookSwapped = false;
        }
    }

    /** Makes the vanilla renderer draw the player model (and hide the first-person hand) while in freecam. */
    @Inject(method = "isThirdPerson", at = @At("HEAD"), cancellable = true)
    private void nitro$showPlayer(CallbackInfoReturnable<Boolean> cir) {
        if (Freecam.active() && Freecam.INSTANCE.showPlayer.on()) cir.setReturnValue(true);
    }
}
