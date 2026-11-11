package com.example.ancientbeam;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.List;

public class DebrisHud {
    private static final int BG = 0xB0102040, BORDER = 0xFF3A8DFF, TXT = 0xFF8FD8FF;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private static void panel(DrawContext c, int x, int y, int w, int h) {
        c.fill(x, y, x + w, y + h, BG);
        c.drawBorder(x, y, w, h, BORDER);
    }

    public static void render(DrawContext ctx, RenderTickCounter tc) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (!Debris.enabled || mc.player == null || mc.options.hudHidden || mc.getDebugHud().shouldShowDebugHud()) return;
        TextRenderer tr = mc.textRenderer;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        List<Debris.Cluster> cl = Debris.clusters;

        // thanh thống kê (góc phải trên)
        String stats = "R=" + Debris.RADIUS + " | thấy: " + Debris.seen + "  đào: " + Debris.mined
                + "  netherite≈" + (Debris.mined / 4);
        int stw = tr.getWidth(stats) + 8;
        panel(ctx, sw - stw - 4, 4, stw, 14);
        ctx.drawText(tr, stats, sw - stw, 7, TXT, false);

        // cảnh báo lava: hiện kể cả khi không có debris nào
        if (Debris.lavaNear) {
            String w = "⚠ Gần hồ lava lớn";
            int ww = tr.getWidth(w) + 16;
            int wy = sh - 90;
            panel(ctx, sw / 2 - ww / 2, wy, ww, 16);
            ctx.drawText(tr, w, sw / 2 - ww / 2 + 8, wy + 4, 0xFFFFAA00, false);
        }

        if (cl.isEmpty()) return;

        // MỤC TIÊU GẦN NHẤT
        int rows = Math.min(4, cl.size());
        int pw = 190;
        panel(ctx, 4, 4, pw, 14 + rows * 11);
        ctx.drawText(tr, "MỤC TIÊU GẦN NHẤT", 8, 7, TXT, false);
        for (int i = 0; i < rows; i++) {
            Debris.Cluster c = cl.get(i);
            String line = (i + 1) + ") " + (int) c.center.x + " " + (int) c.center.y + " " + (int) c.center.z
                    + "  " + (int) c.dist + "m  x" + c.blocks.size();
            ctx.drawText(tr, line, 8, 19 + i * 11, c == Debris.selected() ? 0xFFFFFF55 : 0xFFFFFFFF, false);
        }

        Debris.Cluster sel = Debris.selected();
        if (sel == null) return;
        Vec3d pp = mc.player.getPos();

        // mũi tên hướng + chênh lệch độ cao
        double dx = sel.center.x - pp.x, dz = sel.center.z - pp.z;
        float tyaw = (float) (Math.atan2(-dx, dz) * 180.0 / Math.PI);
        float rel = MathHelper.wrapDegrees(tyaw - mc.player.getYaw());
        int idx = Math.floorMod(Math.round(rel / 45f), 8);
        int dy = (int) Math.round(sel.center.y - pp.y);
        String h = dy > 2 ? "cao hơn " + dy : dy < -2 ? "thấp hơn " + (-dy) : "ngang tầm";
        int aw = Math.max(70, tr.getWidth(h) + 10);
        int ax = sw / 2 - aw / 2;
        panel(ctx, ax, 4, aw, 38);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(sw / 2f, 9, 0);
        ctx.getMatrices().scale(2f, 2f, 1f);
        ctx.drawText(tr, ARROWS[idx], -tr.getWidth(ARROWS[idx]) / 2, 0, 0xFF55FFFF, false);
        ctx.getMatrices().pop();
        ctx.drawText(tr, h, sw / 2 - tr.getWidth(h) / 2, 30, TXT, false);

        // banner giữa màn hình
        String ban = "ANCIENT DEBRIS // còn " + (int) sel.dist + " block  (cụm " + (cl.indexOf(sel) + 1) + ")  "
                + (sel.buried ? "[KÍN]" : "[LỘ]");
        int bw = tr.getWidth(ban) + 16;
        int by = sh / 2 + 24;
        panel(ctx, sw / 2 - bw / 2, by, bw, 16);
        ctx.drawText(tr, ban, sw / 2 - bw / 2 + 8, by + 4, TXT, false);

    }
}
