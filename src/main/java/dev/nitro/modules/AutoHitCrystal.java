package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.CombatUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Auto Hit Crystal: one click places a crystal on the obsidian/bedrock you look at and breaks it as soon as it exists.
 * "Right click" breaks the crystal that your own right click placed; "Left click on block" places it for you when you
 * left click the block (hit it) and then breaks it.
 */
public final class AutoHitCrystal extends Module {
    public enum Trigger {
        RIGHT("Right click (I place, it breaks)"), LEFT("Left click on block (it places and breaks)");
        private final String label;
        Trigger(String l) { label = l; }
        @Override public String toString() { return label; }
    }

    private final Setting.Choice<Trigger> trigger = new Setting.Choice<>("Trigger", Trigger.RIGHT, Trigger.class);
    private final Setting.Num delay = new Setting.Num("Break delay (ticks after it appears)", 0, 0, 5, 1);
    private final Setting.Bool humanize = new Setting.Bool("Humanize timing", true);
    private final Setting.Num range = new Setting.Num("Break range", 4.5, 3, 6, 0.5);
    private final Setting.Bool autoSwitch = new Setting.Bool("Switch to hotbar crystals", true);

    private BlockPos base;
    private int timer, timeout;
    private boolean prevUse, prevAttack, timerSet;

    public AutoHitCrystal() {
        super("Auto Hit Crystal", "Places and breaks a crystal on the obsidian/bedrock you click.", Category.COMBAT);
        add(trigger, delay, humanize, range, autoSwitch);
        autoSwitch.showIf(() -> trigger.value == Trigger.LEFT);
    }

    @Override
    public void onEnable() {
        base = null;
        prevUse = prevAttack = false;
    }

    @Override
    public void onWorldChanged() {
        base = null;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) return;
        if (mc.currentScreen != null) {
            prevUse = prevAttack = false;
            base = null;
            return;
        }
        boolean use = mc.options.useKey.isPressed();
        boolean attack = mc.options.attackKey.isPressed();
        boolean useEdge = use && !prevUse;
        boolean attackEdge = attack && !prevAttack;
        prevUse = use;
        prevAttack = attack;

        if (base == null) {
            if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
            BlockPos bp = hit.getBlockPos();
            BlockState st = mc.world.getBlockState(bp);
            if (!st.isOf(Blocks.OBSIDIAN) && !st.isOf(Blocks.BEDROCK)) return;

            if (trigger.value == Trigger.RIGHT) {
                if (useEdge && (p.getMainHandStack().isOf(Items.END_CRYSTAL) || p.getOffHandStack().isOf(Items.END_CRYSTAL))) {
                    arm(bp);
                }
            } else if (attackEdge && canPlace(bp)) {
                Hand hand = crystalHand();
                if (hand == null) return;
                if (mc.interactionManager.interactBlock(p, hand, hit).isAccepted()) {
                    p.swingHand(hand);
                    arm(bp);
                }
            }
            return;
        }

        // waiting for the crystal to break
        if (--timeout <= 0) {
            base = null;
            return;
        }
        EndCrystalEntity c = crystalAt(base.up());
        if (c == null) return; // not spawned yet (ping)
        if (!timerSet) {
            timer = delay.i() + (humanize.on() ? ThreadLocalRandom.current().nextInt(0, 2) : 0);
            timerSet = true;
        }
        if (timer > 0) {
            timer--;
            return;
        }
        if (p.getEyePos().squaredDistanceTo(c.getPos()) > range.d() * range.d()) {
            base = null;
            return;
        }
        mc.interactionManager.attackEntity(p, c);
        p.swingHand(Hand.MAIN_HAND);
        base = null;
    }

    private void arm(BlockPos bp) {
        base = bp;
        timeout = 30;
        timerSet = false;
    }

    private boolean canPlace(BlockPos p) {
        if (!mc.world.getBlockState(p.up()).isAir() || !mc.world.getBlockState(p.up(2)).isAir()) return false;
        Box box = new Box(p.getX(), p.getY() + 1, p.getZ(), p.getX() + 1, p.getY() + 3, p.getZ() + 1);
        return mc.world.getOtherEntities(null, box, e -> !e.isSpectator()).isEmpty();
    }

    private Hand crystalHand() {
        if (mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) return Hand.OFF_HAND;
        if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) return Hand.MAIN_HAND;
        int slot = CombatUtil.findHotbar(Items.END_CRYSTAL);
        if (autoSwitch.on() && slot >= 0) {
            CombatUtil.selectHotbar(slot);
            return Hand.MAIN_HAND;
        }
        return null;
    }

    private EndCrystalEntity crystalAt(BlockPos pos) {
        List<EndCrystalEntity> l = mc.world.getEntitiesByClass(EndCrystalEntity.class, new Box(pos), e -> e.isAlive());
        return l.isEmpty() ? null : l.get(0);
    }
}
