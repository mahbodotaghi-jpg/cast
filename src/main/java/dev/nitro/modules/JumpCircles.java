package dev.nitro.modules;

import dev.nitro.module.Module;
import dev.nitro.settings.Setting;
import dev.nitro.util.RenderUtil;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.joml.Matrix4f;

/** Draws a simple local ring at the projected landing location. */
public final class JumpCircles extends Module {
    private final Setting.Num radius = new Setting.Num("Circle radius", 0.65, 0.25, 1.5, 0.05);
    private final Setting.Num width = new Setting.Num("Line width", 2.0, 1.0, 5.0, 0.5);
    private final Setting.Color color = new Setting.Color("Colour", 0xB86CFF);

    public JumpCircles() {
        super("Jump Circles", "Draws a landing marker beneath you while airborne.", Category.RENDER);
        add(radius, width, color);
    }

    @Override public void renderWorld(WorldRenderContext ctx) {
        if (mc.player == null || mc.world == null || mc.player.isSpectator()) return;
        Vec3d p = mc.player.getPos();
        Vec3d start = p.add(0, 0.1, 0);
        Vec3d end = p.add(0, -64, 0);
        BlockHitResult hit = mc.world.raycast(new RaycastContext(start, end,
                RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
        if (hit.getType() == HitResult.Type.MISS) return;
        double groundY = hit.getPos().y + 0.025;

        Vec3d vel = mc.player.getVelocity();
        double height = Math.max(0.0, p.y - groundY);
        double ticks = 0.0;
        if (height > 0.08) {
            double vy = vel.y;
            ticks = (vy + Math.sqrt(vy * vy + 0.16 * height)) / 0.08;
            ticks = Math.max(0.0, Math.min(30.0, ticks));
        }
        double x = p.x + vel.x * ticks;
        double z = p.z + vel.z * ticks;
        if (ctx.matrixStack() == null || ctx.camera() == null) return;
        Vec3d camera = ctx.camera().getPos();
        Matrix4f matrix = ctx.matrixStack().peek().getPositionMatrix();
        RenderUtil.begin();
        BufferBuilder b = RenderUtil.lines(width.f());
        int argb = 0xE0000000 | color.rgb();
        int segments = 64;
        double r = radius.d();
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0 * i / segments;
            double a1 = Math.PI * 2.0 * (i + 1) / segments;
            RenderUtil.line(b, matrix,
                    (float)(x - camera.x + Math.cos(a0) * r), (float)(groundY - camera.y), (float)(z - camera.z + Math.sin(a0) * r),
                    (float)(x - camera.x + Math.cos(a1) * r), (float)(groundY - camera.y), (float)(z - camera.z + Math.sin(a1) * r), argb);
        }
        RenderUtil.draw(b);
        RenderUtil.end();
    }
}
