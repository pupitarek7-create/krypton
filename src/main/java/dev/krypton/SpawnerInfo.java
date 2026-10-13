package dev.krypton;

import net.minecraft.util.math.BlockPos;

/** One spawner (or trial spawner) found in a loaded chunk. */
public final class SpawnerInfo {
    public final BlockPos pos;
    public final String dimension;
    /** Pretty mob name, e.g. "Cave Spider". */
    public final String mob;
    /** Structure guess, e.g. "Mineshaft", "Dungeon", or "Unusual". */
    public final String kind;
    /** True when the mob does not match any natural spawner, a strong player-placed signal. */
    public final boolean unusual;
    public final int nearbyChests;
    public boolean alerted;

    public SpawnerInfo(BlockPos pos, String dimension, String mob, String kind, boolean unusual, int nearbyChests) {
        this.pos = pos;
        this.dimension = dimension;
        this.mob = mob;
        this.kind = kind;
        this.unusual = unusual;
        this.nearbyChests = nearbyChests;
    }
}
