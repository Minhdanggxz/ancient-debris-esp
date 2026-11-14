package com.example.ancientbeam;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.ShaderProgramKeys;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

public class DebrisRenderer {
    public static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Debris.enabled || mc.player == null || (Debris.clusters.isEmpty() && Debris.fakes.isEmpty())) return;

        MatrixStack ms = ctx.matrixStack();
        if (ms == null) { ms = new MatrixStack(); ms.multiplyPositionMatrix(ctx.positionMatrix()); }
        Camera cam = ctx.camera();
        Vec3d c0 = cam.getPos();
        Matrix4f m = ms.peek().getPositionMatrix();
        Debris.Cluster sel = Debris.selected();
        List<Debris.Cluster> cl = Debris.clusters;

        RenderSystem.setShader(ShaderProgramKeys.POSITION_COLOR);
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();

        // --- tia (beam) + viền block, xuyên tường ---
        BufferBuilder q = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        int drawn = 0;
        for (Debris.Cluster c : cl) {
            boolean s = c == sel;
            float a = s ? 0.55f : 0.28f;
            double cx = c.center.x - c0.x, cy = c.center.y - c0.y, cz = c.center.z - c0.z;
            box(q, m, (float) cx - 0.12f, (float) cy, (float) cz - 0.12f,
                    (float) cx + 0.12f, (float) cy + 100f, (float) cz + 0.12f, 0.2f, 0.55f, 1f, a);
            for (BlockPos b : c.blocks) {
                if (drawn++ > 400) break;
                float x = (float) (b.getX() - c0.x), y = (float) (b.getY() - c0.y), z = (float) (b.getZ() - c0.z);
                box(q, m, x - 0.01f, y - 0.01f, z - 0.01f, x + 1.01f, y + 1.01f, z + 1.01f, 0.3f, 0.85f, 1f, s ? 0.4f : 0.25f);
            }
        }
        // debris giả: ô đỏ xuyên tường
        for (BlockPos fb : Debris.fakes) {
            float fx = (float) (fb.getX() - c0.x), fy = (float) (fb.getY() - c0.y), fz = (float) (fb.getZ() - c0.z);
            box(q, m, fx - 0.01f, fy - 0.01f, fz - 0.01f, fx + 1.01f, fy + 1.01f, fz + 1.01f, 1f, 0.1f, 0.1f, 0.5f);
        }
        BufferRenderer.drawWithGlobalProgram(q.end());

        // --- tracer (xanh: debris thật, đỏ: nghi giả) ---
        RenderSystem.lineWidth(2f);
        BufferBuilder l = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);
        Vec3d look = mc.player.getRotationVec(1f).multiply(0.4);
        int n = 0;
        for (Debris.Cluster c : cl) {
            if (n++ >= 8) break;
            boolean s = c == sel;
            float a = s ? 1f : 0.35f;
            l.vertex(m, (float) look.x, (float) look.y, (float) look.z).color(0.3f, 0.8f, 1f, a);
            l.vertex(m, (float) (c.center.x - c0.x), (float) (c.center.y - c0.y), (float) (c.center.z - c0.z)).color(0.3f, 0.8f, 1f, a);
        }
        int fn = 0;
        for (BlockPos fb : Debris.fakes) {
            if (fn++ >= 8) break;
            l.vertex(m, (float) look.x, (float) look.y, (float) look.z).color(1f, 0.15f, 0.15f, 0.9f);
            l.vertex(m, (float) (fb.getX() + 0.5 - c0.x), (float) (fb.getY() + 0.5 - c0.y), (float) (fb.getZ() + 0.5 - c0.z)).color(1f, 0.15f, 0.15f, 0.9f);
        }
        BufferRenderer.drawWithGlobalProgram(l.end());

        if (!cl.isEmpty()) {
        // --- nhãn khoảng cách ---
        VertexConsumerProvider.Immediate imm = mc.getBufferBuilders().getEntityVertexConsumers();
        TextRenderer tr = mc.textRenderer;
        n = 0;
        for (Debris.Cluster c : cl) {
            if (n++ >= 8) break;
            String t = (int) c.dist + "m";
            double rx = c.center.x - c0.x, ry = c.center.y - c0.y + 1.5, rz = c.center.z - c0.z;
            float s = 0.025f * MathHelper.clamp((float) c.dist / 8f, 1f, 14f);
            ms.push();
            ms.translate(rx, ry, rz);
            ms.multiply(cam.getRotation());
            ms.scale(s, -s, s);
            tr.draw(t, -tr.getWidth(t) / 2f, 0, c == sel ? 0xFF55FFFF : 0xFFFFFF55, false,
                    ms.peek().getPositionMatrix(), imm, TextRenderer.TextLayerType.SEE_THROUGH, 0, 0xF000F0);
            ms.pop();
        }
        imm.draw();

        }
        RenderSystem.lineWidth(1f);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    private static void box(BufferBuilder b, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                            float r, float g, float bl, float a) {
        quad(b, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, bl, a); // dưới
        quad(b, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, bl, a); // trên
        quad(b, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, bl, a); // bắc
        quad(b, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, bl, a); // nam
        quad(b, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, bl, a); // tây
        quad(b, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, bl, a); // đông
    }

    private static void quad(BufferBuilder b, Matrix4f m,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float r, float g, float bl, float a) {
        b.vertex(m, ax, ay, az).color(r, g, bl, a);
        b.vertex(m, bx, by, bz).color(r, g, bl, a);
        b.vertex(m, cx, cy, cz).color(r, g, bl, a);
        b.vertex(m, dx, dy, dz).color(r, g, bl, a);
    }
}
