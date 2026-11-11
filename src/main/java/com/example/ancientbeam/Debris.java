package com.example.ancientbeam;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

public class Debris {
    public static final int RADIUS = 256;
    public static final int LAVA_WARN = 40; // số block lava trong hộp 11x11x11 quanh người chơi

    public static boolean enabled = true;
    public static boolean sound = true;          // phím L bật/tắt
    public static final float SOUND_VOLUME = 1.0f;
    public static int mined = 0;
    public static int seen = 0;
    public static boolean lavaNear = false;
    public static final List<Cluster> clusters = new ArrayList<>();

    private static long selKey = Long.MIN_VALUE;
    private static final Set<Long> known = new HashSet<>();
    private static final Set<Long> notified = new HashSet<>();
    private static ClientWorld lastWorld;
    private static int tick = 0;

    public static class Cluster {
        public final List<BlockPos> blocks;
        public final Vec3d center;
        public final boolean buried;
        public final long key;
        public double dist;

        Cluster(List<BlockPos> blocks, ClientWorld w) {
            this.blocks = blocks;
            double x = 0, y = 0, z = 0;
            long min = Long.MAX_VALUE;
            boolean exposed = false;
            for (BlockPos b : blocks) {
                x += b.getX() + 0.5; y += b.getY() + 0.5; z += b.getZ() + 0.5;
                min = Math.min(min, b.asLong());
                if (!exposed) for (Direction d : Direction.values())
                    if (w.getBlockState(b.offset(d)).isAir()) { exposed = true; break; }
            }
            int n = blocks.size();
            this.center = new Vec3d(x / n, y / n, z / n);
            this.buried = !exposed;
            this.key = min;
        }
    }

    public static Cluster selected() {
        if (clusters.isEmpty()) return null;
        for (Cluster c : clusters) if (c.key == selKey) return c;
        selKey = clusters.get(0).key;
        return clusters.get(0);
    }

    public static void cycle() {
        if (clusters.isEmpty()) return;
        int i = clusters.indexOf(selected());
        selKey = clusters.get((i + 1) % clusters.size()).key;
    }

    public static void tick(MinecraftClient mc) {
        if (mc.world == null || mc.player == null) { lastWorld = null; return; }
        if (mc.world != lastWorld) {
            lastWorld = mc.world;
            known.clear(); notified.clear(); clusters.clear();
            mined = 0; seen = 0; selKey = Long.MIN_VALUE;
        }
        if (!enabled) return;
        tick++;
        if (tick % 40 == 1) scan(mc);
        if (tick % 20 == 0) lava(mc);
        Vec3d pp = mc.player.getPos();
        for (Cluster c : clusters) c.dist = c.center.distanceTo(pp);
        clusters.sort(Comparator.comparingDouble(c -> c.dist));
    }

    private static void scan(MinecraftClient mc) {
        ClientWorld w = mc.world;
        Vec3d pp = mc.player.getPos();
        int pcx = mc.player.getBlockX() >> 4, pcz = mc.player.getBlockZ() >> 4;
        int cr = (RADIUS >> 4) + 1;
        double r2 = (double) RADIUS * RADIUS;
        Set<Long> found = new HashSet<>();

        for (int cx = pcx - cr; cx <= pcx + cr; cx++) {
            for (int cz = pcz - cr; cz <= pcz + cr; cz++) {
                WorldChunk ch = w.getChunkManager().getWorldChunk(cx, cz);
                if (ch == null) continue;
                ChunkSection[] secs = ch.getSectionArray();
                for (int i = 0; i < secs.length; i++) {
                    ChunkSection s = secs[i];
                    if (s == null || s.isEmpty()) continue;
                    if (!s.hasAny(st -> st.isOf(Blocks.ANCIENT_DEBRIS))) continue;
                    int baseY = ch.getBottomY() + i * 16;
                    for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
                        if (!s.getBlockState(x, y, z).isOf(Blocks.ANCIENT_DEBRIS)) continue;
                        BlockPos p = new BlockPos(cx * 16 + x, baseY + y, cz * 16 + z);
                        if (p.toCenterPos().squaredDistanceTo(pp) <= r2) found.add(p.asLong());
                    }
                }
            }
        }

        // đếm block đã đào: biến mất khỏi danh sách trong khi chunk vẫn load và block không còn là debris
        for (long k : known) {
            if (found.contains(k)) continue;
            BlockPos p = BlockPos.fromLong(k);
            if (w.getChunkManager().getWorldChunk(p.getX() >> 4, p.getZ() >> 4) != null
                    && !w.getBlockState(p).isOf(Blocks.ANCIENT_DEBRIS)) mined++;
        }
        known.clear();
        known.addAll(found);
        seen = found.size();

        // gom cụm (26 hướng kề nhau)
        clusters.clear();
        Set<Long> visited = new HashSet<>();
        for (long k : found) {
            if (!visited.add(k)) continue;
            List<BlockPos> blocks = new ArrayList<>();
            ArrayDeque<Long> q = new ArrayDeque<>();
            q.add(k);
            while (!q.isEmpty()) {
                BlockPos b = BlockPos.fromLong(q.poll());
                blocks.add(b);
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    long n = b.add(dx, dy, dz).asLong();
                    if (found.contains(n) && visited.add(n)) q.add(n);
                }
            }
            clusters.add(new Cluster(blocks, w));
        }

        // thông báo chat khi có debris mới
        List<BlockPos> fresh = new ArrayList<>();
        for (long k : found) if (notified.add(k)) fresh.add(BlockPos.fromLong(k));
        if (!fresh.isEmpty()) {
            StringBuilder sb = new StringBuilder("§b[AncientBeam] §fTìm thấy §e" + fresh.size() + "§f ancient debris mới: ");
            for (int i = 0; i < Math.min(5, fresh.size()); i++) {
                BlockPos p = fresh.get(i);
                sb.append("§a").append(p.getX()).append(' ').append(p.getY()).append(' ').append(p.getZ()).append("§f; ");
            }
            if (fresh.size() > 5) sb.append("...");
            mc.player.sendMessage(Text.literal(sb.toString()), false);
            if (sound) playSound(mc);
        }
    }


    private static final Identifier CUSTOM_FILE = Identifier.of("ancientbeam", "sounds/ding.ogg");
    private static final SoundEvent CUSTOM_SOUND = SoundEvent.of(Identifier.of("ancientbeam", "debris_found"));

    private static void playSound(MinecraftClient mc) {
        // Có file ding.ogg của bạn trong mod -> dùng file đó, không thì dùng tiếng mặc định của Minecraft
        if (mc.getResourceManager().getResource(CUSTOM_FILE).isPresent()) {
            mc.getSoundManager().play(PositionedSoundInstance.master(CUSTOM_SOUND, 1.0f, SOUND_VOLUME));
            return;
        }
        boolean anyExposed = false;
        for (Cluster c : clusters) if (!c.buried) { anyExposed = true; break; }
        float pitch = anyExposed ? 1.6f : 1.0f;
        mc.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, pitch, SOUND_VOLUME));
    }

    private static void lava(MinecraftClient mc) {
        BlockPos p = mc.player.getBlockPos();
        int n = 0;
        for (int x = -5; x <= 5; x++) for (int y = -5; y <= 5; y++) for (int z = -5; z <= 5; z++)
            if (mc.world.getFluidState(p.add(x, y, z)).isIn(FluidTags.LAVA)) n++;
        lavaNear = n >= LAVA_WARN;
    }
}
