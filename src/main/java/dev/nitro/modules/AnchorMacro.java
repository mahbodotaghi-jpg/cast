package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.CombatUtil;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Anchor Macro. Look at a respawn anchor and (optionally) hold right click:
 * <ol>
 *   <li>CHARGE - while the anchor has fewer charges than you asked for, it switches to glowstone and right clicks.</li>
 *   <li>EXPLODE - once it has enough charges, it switches to your totem slot and right clicks to detonate it.</li>
 * </ol>
 * Every step reads the anchor's real charge level from the world, so lag or a missed click just repeats the step
 * instead of getting the macro out of sync. Anchors only explode outside the Nether, so it does nothing there.
 */
public final class AnchorMacro extends Module {
    private final Setting.Num charges = new Setting.Num("Charges before explode", 1, 1, 4, 1);
    private final Setting.Num explodeSlot = new Setting.Num("Totem slot (hotbar 1-9)", 1, 1, 9, 1);
    private final Setting.Bool autoTotem = new Setting.Bool("Find totem in hotbar automatically", false);
    private final Setting.Num delay = new Setting.Num("Speed: delay between steps (ticks)", 1, 0, 10, 1);
    private final Setting.Bool needUse = new Setting.Bool("Only while holding right click", true);
    private final Setting.Bool needTotem = new Setting.Bool("Require offhand totem", true);
    private final Setting.Bool swapBack = new Setting.Bool("Switch back after explode", false);

    /** Ticks left until the next step is allowed. */
    private int cooldown;
    /** Hotbar slot you were holding before the macro touched your hotbar, or -1 when it has not changed anything. */
    private int originalSlot = -1;

    public AnchorMacro() {
        super("Anchor Macro", "Charges the anchor you look at and ignites it with your hotbar totem.", Category.COMBAT);
        add(charges, explodeSlot, autoTotem, delay, needUse, needTotem, swapBack);
        explodeSlot.showIf(() -> !autoTotem.on());
    }

    @Override
    public void onEnable() {
        cooldown = 0;
        originalSlot = -1;
    }

    @Override
    public void onDisable() {
        finish();
    }

    @Override
    public void onWorldChanged() {
        cooldown = 0;
        originalSlot = -1;
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        if (cooldown > 0) cooldown--;

        BlockHitResult hit = targetAnchor();
        if (hit == null || !conditionsMet()) {
            finish(); // looked away / let go / anchor gone: put the hotbar back if asked to
            return;
        }
        if (cooldown > 0) return;

        BlockState state = mc.world.getBlockState(hit.getBlockPos());
        int have = state.get(RespawnAnchorBlock.CHARGES);

        boolean exploding = have >= charges.i();
        int slot = exploding ? explodeHotbarSlot() : CombatUtil.findHotbar(Items.GLOWSTONE);
        if (slot < 0) return;                                              // nothing to charge with
        if (exploding && mc.player.getInventory().getStack(slot).isOf(Items.GLOWSTONE)) return; // would not explode it

        if (originalSlot < 0) originalSlot = mc.player.getInventory().selectedSlot;
        CombatUtil.selectHotbar(slot);

        if (mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit).isAccepted()) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        if (exploding) finish();
        cooldown = delay.i();
    }

    /** The anchor under the crosshair, or null when you are not looking at one (or the macro must not run here). */
    private BlockHitResult targetAnchor() {
        if (mc.currentScreen != null) return null;
        if (mc.world.getDimension().respawnAnchorWorks()) return null; // Nether: anchors do not explode
        if (!(mc.crosshairTarget instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        return mc.world.getBlockState(hit.getBlockPos()).isOf(Blocks.RESPAWN_ANCHOR) ? hit : null;
    }

    private boolean conditionsMet() {
        if (needUse.on() && !mc.options.useKey.isPressed()) return false;
        return !needTotem.on() || mc.player.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING);
    }

    /** Hotbar slot used to detonate: the first totem in the hotbar (auto) or the slot set in the settings. */
    private int explodeHotbarSlot() {
        if (autoTotem.on()) {
            int found = CombatUtil.findHotbar(Items.TOTEM_OF_UNDYING);
            if (found >= 0) return found;
        }
        return explodeSlot.i() - 1;
    }

    /** Ends the current sequence; switches back to the slot you started on when that option is on. */
    private void finish() {
        if (swapBack.on() && originalSlot >= 0 && mc.player != null) CombatUtil.selectHotbar(originalSlot);
        originalSlot = -1;
    }
}
