package dev.krypton;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.LinkedHashMap;
import java.util.Map;

/** Everything Krypton knows about one scanned chunk. */
public final class ChunkInfo {
    public enum Age { UNKNOWN, NEW, OLD }

    public final ChunkPos pos;
    public final String dimension;
    public int score;
    public int minY = Integer.MAX_VALUE;
    public int maxY = Integer.MIN_VALUE;
    public boolean hasFluid;
    public boolean alerted;
    public boolean loaded = true;
    public long loadTick;
    public Age age = Age.UNKNOWN;
    /** Block kind to count, sorted by insertion (already ordered by weight when finished). */
    public Map<String, Integer> counts = new LinkedHashMap<>();
    public BlockPos anchor;

    public ChunkInfo(ChunkPos pos, String dimension) {
        this.pos = pos;
        this.dimension = dimension;
    }

    public int centerX() { return pos.getStartX() + 8; }
    public int centerZ() { return pos.getStartZ() + 8; }

    public String summary(int max) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (n++ >= max) break;
            if (sb.length() > 0) sb.append("  ");
            sb.append(e.getKey()).append(" x").append(e.getValue());
        }
        return sb.toString();
    }
}
