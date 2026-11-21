package dev.nitro.mixin;

import dev.nitro.util.AttackHooks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets Shield Breaker / Mace Swap / Breach Swap pick the weapon right before an attack, and Fall Reset see hits. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void nitro$attackPre(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (player == MinecraftClient.getInstance().player) AttackHooks.pre(target);
    }

    @Inject(method = "attackEntity", at = @At("RETURN"))
    private void nitro$attackPost(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (player == MinecraftClient.getInstance().player) AttackHooks.post(target);
    }
}
