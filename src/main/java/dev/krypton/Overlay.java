package dev.krypton;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.Vec3d;

import java.util.List;

/**
 * Screen-space ESP: projects chunks and spawners onto the HUD using the camera
 * orientation, so it does not depend on world-render internals.
 */
public final class Overlay {
    // Palette
    private static final int SPAWNER = 0xFF5EEAD4;
    private static final int SPAWNER_UNUSUAL = 0xFFFB7185;
    private static final int CHUNK = 0xFFFBBF24;
    private static final int CHUNK_NEW = 0xFF60A5FA;
    private static final int CHUNK_OLD = 0xFFA78BFA;
    private static final int PANEL = 0xB0101418;
    private static final int TEXT = 0xFFE5E7EB;
    private static final int MUTED = 0xFF9CA3AF;

    private static final int MAX_TRACERS = 8;

    public static void render(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        if (mc.currentScreen != null) return;

        Camera cam = mc.gameRenderer.getCamera();
        Vec3d eye = new Vec3d(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
        Projector proj = new Projector(mc, cam, eye);
        String dim = Scanner.currentDimension();
        TextRenderer tr = mc.textRenderer;

        int tracersLeft = MAX_TRACERS;

        if (Config.spawnerFinder) {
            List<SpawnerInfo> spawners = Scanner.spawners(dim);
            for (SpawnerInfo s : spawners) {
                Vec3d p = Vec3d.ofCenter(s.pos);
                double dist = p.distanceTo(eye);
                if (dist > Config.maxDistance) continue;
                int color = s.unusual ? SPAWNER_UNUSUAL : SPAWNER;
                float[] pt = proj.project(p);
                if (pt == null) continue;
                if (Config.boxes) marker(ctx, pt[0], pt[1], dist, color);
                if (Config.labels) {
                    String text = s.mob + "  " + (int) dist + "m";
                    label(ctx, tr, text, (int) pt[0], (int) pt[1] - 14, color);
                    label(ctx, tr, s.kind, (int) pt[0], (int) pt[1] - 24, MUTED);
                }
                if (Config.tracers && tracersLeft > 0) {
                    tracer(ctx, mc, pt[0], pt[1], color);
                    tracersLeft--;
                }
            }
        }

        if (Config.chunkFinder) {
            for (ChunkInfo c : Scanner.susChunks(dim)) {
                if (!c.loaded) continue;
                int y = c.anchor != null ? c.anchor.getY() : (int) eye.y;
                Vec3d p = new Vec3d(c.centerX(), y, c.centerZ());
                double dist = p.distanceTo(eye);
                if (dist > Config.maxDistance) continue;
                float[] pt = proj.project(p);
                if (pt == null) continue;
                int color = CHUNK;
                if (Config.ageOverlay) {
                    if (c.age == ChunkInfo.Age.NEW) color = CHUNK_NEW;
                    else if (c.age == ChunkInfo.Age.OLD) color = CHUNK_OLD;
                }
                if (Config.boxes) marker(ctx, pt[0], pt[1], dist, color);
                if (Config.labels) {
                    label(ctx, tr, "Sus chunk  " + c.score + "  " + (int) dist + "m", (int) pt[0], (int) pt[1] - 14, color);
                    String sum = c.summary(2);
                    if (!sum.isEmpty()) label(ctx, tr, sum, (int) pt[0], (int) pt[1] - 24, MUTED);
                }
            }
        }

        if (Config.hudList) drawList(ctx, mc, tr, dim);
    }

    // ------------------------------------------------------------ HUD list

    private static void drawList(DrawContext ctx, MinecraftClient mc, TextRenderer tr, String dim) {
        List<SpawnerInfo> spawners = Config.spawnerFinder ? Scanner.spawners(dim) : List.of();
        List<ChunkInfo> chunks = Config.chunkFinder ? Scanner.susChunks(dim) : List.of();
        if (spawners.isEmpty() && chunks.isEmpty()) return;

        int rows = Math.min(spawners.size(), 5) + Math.min(chunks.size(), 4);
        int w = 170;
        int h = 18 + rows * 11 + 4;
        int x = 6, y = 6;

        ctx.fill(x, y, x + w, y + h, PANEL);
        ctx.fill(x, y, x + 2, y + h, 0xFFA78BFA);
        ctx.drawText(tr, "KRYPTON", x + 8, y + 5, TEXT, false);

        int ly = y + 18;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        int n = 0;
        for (SpawnerInfo s : spawners) {
            if (n++ >= 5) break;
            int d = (int) Math.sqrt(s.pos.getSquaredDistance(px, py, pz));
            ctx.drawText(tr, s.mob + " " + d + "m", x + 8, ly, s.unusual ? SPAWNER_UNUSUAL : SPAWNER, false);
            ly += 11;
        }
        n = 0;
        for (ChunkInfo c : chunks) {
            if (n++ >= 4) break;
            int d = (int) Math.sqrt(Math.pow(c.centerX() - px, 2) + Math.pow(c.centerZ() - pz, 2));
            ctx.drawText(tr, "Chunk " + c.score + "  " + d + "m", x + 8, ly, CHUNK, false);
            ly += 11;
        }
    }

    // ------------------------------------------------------------ drawing helpers

    private static void marker(DrawContext ctx, float sx, float sy, double dist, int color) {
        int half = (int) Math.max(4, Math.min(22, 220 / Math.max(dist, 1)));
        int x = (int) sx, y = (int) sy;
        int x1 = x - half, y1 = y - half, x2 = x + half, y2 = y + half;
        ctx.fill(x1, y1, x2, y1 + 1, color);
        ctx.fill(x1, y2 - 1, x2, y2, color);
        ctx.fill(x1, y1, x1 + 1, y2, color);
        ctx.fill(x2 - 1, y1, x2, y2, color);
        ctx.fill(x - 1, y - 1, x + 1, y + 1, color);
    }

    private static void label(DrawContext ctx, TextRenderer tr, String text, int cx, int y, int color) {
        int w = tr.getWidth(text);
        int x = cx - w / 2;
        ctx.fill(x - 3, y - 2, x + w + 3, y + 9, PANEL);
        ctx.drawText(tr, text, x, y, color, false);
    }

    private static void tracer(DrawContext ctx, MinecraftClient mc, float tx, float ty, int color) {
        float sx = mc.getWindow().getScaledWidth() / 2f;
        float sy = mc.getWindow().getScaledHeight();
        float dx = tx - sx, dy = ty - sy;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        int steps = (int) Math.min(600, len / 3);
        int faded = (color & 0x00FFFFFF) | 0x99000000;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) Math.max(steps, 1);
            int x = (int) (sx + dx * t);
            int y = (int) (sy + dy * t);
            ctx.fill(x, y, x + 1, y + 1, faded);
        }
    }

    // ------------------------------------------------------------ projection

    private static final class Projector {
        final Vec3d eye;
        final double fx, fy, fz, rx, ry, rz, ux, uy, uz;
        final double tanHalf, aspect;
        final int w, h;

        Projector(MinecraftClient mc, Camera cam, Vec3d eyePos) {
            eye = eyePos;
            double yaw = Math.toRadians(cam.getYaw());
            double pitch = Math.toRadians(cam.getPitch());
            fx = -Math.sin(yaw) * Math.cos(pitch);
            fy = -Math.sin(pitch);
            fz = Math.cos(yaw) * Math.cos(pitch);
            rx = -Math.cos(yaw);
            ry = 0;
            rz = -Math.sin(yaw);
            // up = right x forward
            ux = ry * fz - rz * fy;
            uy = rz * fx - rx * fz;
            uz = rx * fy - ry * fx;
            w = mc.getWindow().getScaledWidth();
            h = mc.getWindow().getScaledHeight();
            aspect = (double) w / h;
            tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        }

        /** Returns {screenX, screenY} or null when the point is behind the camera. */
        float[] project(Vec3d p) {
            double dx = p.x - eye.x, dy = p.y - eye.y, dz = p.z - eye.z;
            double cz = dx * fx + dy * fy + dz * fz;
            if (cz < 0.1) return null;
            double cx = dx * rx + dy * ry + dz * rz;
            double cy = dx * ux + dy * uy + dz * uz;
            double nx = cx / (cz * tanHalf * aspect);
            double ny = cy / (cz * tanHalf);
            if (Math.abs(nx) > 1.4 || Math.abs(ny) > 1.4) return null;
            return new float[]{(float) (w / 2.0 * (1 + nx)), (float) (h / 2.0 * (1 - ny))};
        }
    }

    private Overlay() {}
}
