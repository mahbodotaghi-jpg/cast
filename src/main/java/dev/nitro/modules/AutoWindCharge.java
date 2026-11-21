package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.CombatUtil;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Auto Wind Charge: bind a key to it. Pressing it throws a wind charge at your feet (the server sees a straight-down
 * throw, your camera does not move), then switches to the mace and, on the way down, hits the nearest player once you
 * have fallen far enough and are in reach. It switches itself off when the combo ends.
 */
public final class AutoWindCharge extends Module {
    private final Setting.Num minHeight = new Setting.Num("Min smash height (fall distance)", 3.0, 1.5, 20, 0.5);
    private final Setting.Bool autoAttack = new Setting.Bool("Attack on the way down", true);
    private final Setting.Num reach = new Setting.Num("Attack reach", 3.0, 2.0, 3.5, 0.1);
    private final Setting.Num targetRange = new Setting.Num("Target range", 8, 3, 16, 1);
    private final Setting.Bool useMace = new Setting.Bool("Switch to mace after the launch", true);
    private final Setting.Bool switchBack = new Setting.Bool("Switch back at the end", true);

    private int ticks, airTicks, backSlot = -1, maceSlot = -1;
    private boolean airborne, swapped;

    public AutoWindCharge() {
        super("Auto Wind Charge", "Press its key: wind charge at your feet, then a mace smash on the way down.", Category.COMBAT);
        add(minHeight, autoAttack, reach, targetRange, useMace, switchBack);
        reach.showIf(autoAttack::on);
        targetRange.showIf(autoAttack::on);
    }

    @Override
    public void onEnable() {
        ticks = airTicks = 0;
        airborne = swapped = false;
        backSlot = -1;
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) {
            setEnabled(false);
            return;
        }
        int wind = CombatUtil.findHotbar(Items.WIND_CHARGE);
        if (wind < 0) {
            p.sendMessage(Text.literal("[NITRO] Auto Wind Charge: no wind charge in the hotbar").formatted(Formatting.RED), false);
            setEnabled(false);
            return;
        }
        maceSlot = CombatUtil.findHotbar(Items.MACE);
        backSlot = p.getInventory().selectedSlot;
        if (!CombatUtil.useSilently(wind, p.getYaw(), 90f, false)) {
            p.sendMessage(Text.literal("[NITRO] Auto Wind Charge: the throw was refused (cooldown?)").formatted(Formatting.RED), false);
            setEnabled(false);
        }
    }

    @Override
    public void onDisable() {
        if (switchBack.on() && swapped && backSlot >= 0) CombatUtil.selectHotbar(backSlot);
        backSlot = -1;
        swapped = false;
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = mc.player;
        if (p == null || mc.world == null || mc.interactionManager == null) {
            setEnabled(false);
            return;
        }
        ticks++;
        if (ticks > 120 || (!airborne && ticks > 40)) { // never got launched / combo took too long
            setEnabled(false);
            return;
        }
        if (!p.isOnGround()) {
            airborne = true;
            airTicks++;
        }
        if (airborne && p.isOnGround() && airTicks > 4) {
            setEnabled(false);
            return;
        }
        if (useMace.on() && !swapped && airborne && maceSlot >= 0) {
            CombatUtil.selectHotbar(maceSlot);
            swapped = true;
        }
        if (!autoAttack.on() || !airborne) return;
        if (p.isOnGround() || p.getVelocity().y > -0.05 || p.fallDistance < minHeight.d()) return;
        if (!p.getMainHandStack().isOf(Items.MACE)) return;

        AbstractClientPlayerEntity target = null;
        double bestD = targetRange.d();
        for (AbstractClientPlayerEntity t : mc.world.getPlayers()) {
            if (t == p || !t.isAlive() || t.isSpectator()) continue;
            double d = p.distanceTo(t);
            if (d <= bestD) {
                bestD = d;
                target = t;
            }
        }
        if (target == null) return;
        if (distanceToBox(p.getEyePos(), target.getBoundingBox()) > reach.d()) return;

        mc.interactionManager.attackEntity(p, target);
        p.swingHand(Hand.MAIN_HAND);
        setEnabled(false);
    }

    private static double distanceToBox(Vec3d from, Box b) {
        double x = MathHelper.clamp(from.x, b.minX, b.maxX);
        double y = MathHelper.clamp(from.y, b.minY, b.maxY);
        double z = MathHelper.clamp(from.z, b.minZ, b.maxZ);
        return from.distanceTo(new Vec3d(x, y, z));
    }
}
