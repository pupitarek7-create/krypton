package dev.krypton;

import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.AbstractBannerBlock;
import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.AbstractSignBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BeaconBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DispenserBlock;
import net.minecraft.block.EnchantingTableBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.ObserverBlock;
import net.minecraft.block.PistonBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TntBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BiomeTags;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Chunk + spawner scanning. Everything except {@link #onBlockPacket} runs on the client thread.
 */
public final class Scanner {
    public static final Map<Long, ChunkInfo> CHUNKS = new ConcurrentHashMap<>();
    public static final Map<Long, List<SpawnerInfo>> SPAWNERS = new ConcurrentHashMap<>();

    /** Chunks that received a fluid-flow update (written from the network thread, so concurrent). */
    private static final Map<Long, Long> FLUID_SEEN = new ConcurrentHashMap<>();
    private static final ArrayDeque<WorldChunk> QUEUE = new ArrayDeque<>();
    private static final IdentityHashMap<Block, Integer> WEIGHT_MEMO = new IdentityHashMap<>();

    /** Ticks after load during which a fluid flow update means the chunk is new. */
    private static final int NEW_WINDOW = 240;
    private static final int MAX_STORED = 6000;

    private static long tick;

    // ---------------------------------------------------------------- lifecycle

    public static void queue(WorldChunk chunk) {
        QUEUE.add(chunk);
    }

    public static void unloaded(WorldChunk chunk) {
        long key = chunk.getPos().toLong();
        ChunkInfo info = CHUNKS.get(key);
        if (info != null) {
            // Keep interesting chunks around so the finder remembers them.
            if (info.score >= Config.threshold) info.loaded = false;
            else CHUNKS.remove(key);
        }
        FLUID_SEEN.remove(key);
    }

    public static void clear() {
        CHUNKS.clear();
        SPAWNERS.clear();
        FLUID_SEEN.clear();
        QUEUE.clear();
    }

    public static void tick(MinecraftClient client) {
        tick++;
        if (client.world == null) return;

        // A few chunks per tick keeps the frame time flat.
        int budget = 3;
        while (budget-- > 0 && !QUEUE.isEmpty()) {
            WorldChunk chunk = QUEUE.poll();
            if (chunk == null) break;
            scan(client.world, chunk);
        }

        if (tick % 10 == 0) updateAges();
        if (CHUNKS.size() > MAX_STORED) CHUNKS.values().removeIf(c -> !c.loaded && c.score < Config.threshold * 2);
    }

    // ---------------------------------------------------------------- old/new chunk heuristic

    /** Called for every block update packet. A flowing fluid right after chunk load means a new chunk. */
    public static void onBlockPacket(BlockPos pos, BlockState state) {
        if (state == null || state.getFluidState().isEmpty() || state.getFluidState().isStill()) return;
        FLUID_SEEN.put(new ChunkPos(pos).toLong(), tick);
    }

    private static void updateAges() {
        for (Map.Entry<Long, Long> e : FLUID_SEEN.entrySet()) {
            ChunkInfo info = CHUNKS.get(e.getKey());
            if (info == null || info.age == ChunkInfo.Age.NEW) continue;
            if (e.getValue() - info.loadTick <= NEW_WINDOW) info.age = ChunkInfo.Age.NEW;
        }
        for (ChunkInfo info : CHUNKS.values()) {
            if (info.age != ChunkInfo.Age.UNKNOWN || !info.loaded) continue;
            // Only call a chunk old when it had fluid that could have flowed but never did.
            if (info.hasFluid && tick - info.loadTick > NEW_WINDOW) info.age = ChunkInfo.Age.OLD;
        }
    }

    // ---------------------------------------------------------------- scanning

    private static void scan(ClientWorld world, WorldChunk chunk) {
        if (chunk.getWorld() != world) return;
        String dim = world.getRegistryKey().getValue().toString();
        ChunkPos cp = chunk.getPos();
        long key = cp.toLong();

        ChunkInfo info = new ChunkInfo(cp, dim);
        info.loadTick = tick;

        Map<String, Integer> counts = new HashMap<>();
        int playerScore = 0;
        int bamboo = 0;
        int amethyst = 0;
        int amethystHigh = 0;
        BlockPos bambooPos = null;

        ChunkSection[] sections = chunk.getSectionArray();
        int bottomSection = chunk.getBottomSectionCoord();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            if (section == null || section.isEmpty()) continue;
            if (!info.hasFluid && section.hasAny(st -> !st.getFluidState().isEmpty())) info.hasFluid = true;
            if (!section.hasAny(Scanner::interesting)) continue;

            int baseY = (bottomSection + i) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        Block block = section.getBlockState(x, y, z).getBlock();
                        int worldY = baseY + y;

                        if (block == Blocks.BAMBOO || block == Blocks.BAMBOO_SAPLING) {
                            bamboo++;
                            if (bambooPos == null) bambooPos = new BlockPos(cp.getStartX() + x, worldY, cp.getStartZ() + z);
                            continue;
                        }
                        if (isAmethyst(block)) {
                            amethyst++;
                            if (worldY > 40) amethystHigh++;
                            continue;
                        }
                        int w = weight(block);
                        if (w <= 0) continue;

                        playerScore += w;
                        counts.merge(Registries.BLOCK.getId(block).getPath(), 1, Integer::sum);
                        info.minY = Math.min(info.minY, worldY);
                        info.maxY = Math.max(info.maxY, worldY);
                        if (info.anchor == null) info.anchor = new BlockPos(cp.getStartX() + x, worldY, cp.getStartZ() + z);
                    }
                }
            }
        }

        int score = Math.min(playerScore, 60);

        if (bamboo >= 2 && bambooPos != null) {
            boolean jungle = world.getBiome(bambooPos).isIn(BiomeTags.IS_JUNGLE);
            if (!jungle) {
                score += 6 + Math.min(bamboo / 4, 10);
                counts.put("bamboo (off-biome)", bamboo);
                touch(info, bambooPos);
            }
        }
        if (amethystHigh >= 1) {
            // Geodes only generate low, so amethyst up here was moved or farmed.
            score += 8 + Math.min(amethystHigh, 10);
            counts.put("amethyst (above geode height)", amethystHigh);
        } else if (amethyst >= 40) {
            score += 3;
            counts.put("amethyst (large amount)", amethyst);
        }

        info.score = score;
        info.counts = sortByValue(counts);
        if (info.anchor == null && bambooPos != null) info.anchor = bambooPos;

        // Keep an existing age verdict if we are rescanning.
        ChunkInfo prev = CHUNKS.get(key);
        if (prev != null) {
            info.age = prev.age;
            info.alerted = prev.alerted;
        }
        CHUNKS.put(key, info);

        scanSpawners(world, chunk, dim, key);

        if (Config.notify && Config.chunkFinder && !info.alerted && info.score >= Config.threshold) {
            info.alerted = true;
            say(Text.literal("Suspicious chunk ").formatted(Formatting.YELLOW)
                    .append(Text.literal("[" + info.centerX() + ", " + info.centerZ() + "]").formatted(Formatting.WHITE))
                    .append(Text.literal("  score " + info.score + "  " + info.summary(3)).formatted(Formatting.GRAY)), false);
        }
    }

    private static void touch(ChunkInfo info, BlockPos p) {
        info.minY = Math.min(info.minY, p.getY());
        info.maxY = Math.max(info.maxY, p.getY());
    }

    private static void scanSpawners(ClientWorld world, WorldChunk chunk, String dim, long key) {
        List<BlockPos> chests = new ArrayList<>();
        List<BlockEntity> found = new ArrayList<>();
        for (BlockEntity be : chunk.getBlockEntities().values()) {
            if (be instanceof ChestBlockEntity) chests.add(be.getPos());
            BlockState st = be.getCachedState();
            if (st.isOf(Blocks.SPAWNER) || st.isOf(Blocks.TRIAL_SPAWNER)) found.add(be);
        }

        if (found.isEmpty()) {
            SPAWNERS.remove(key);
            return;
        }

        List<SpawnerInfo> previous = SPAWNERS.get(key);
        List<SpawnerInfo> list = new ArrayList<>();
        for (BlockEntity be : found) {
            BlockPos pos = be.getPos();
            String mobId = "pig";
            boolean trial = be.getCachedState().isOf(Blocks.TRIAL_SPAWNER);
            if (!trial) {
                try {
                    NbtCompound nbt = be.createNbt(world.getRegistryManager());
                    mobId = nbt.getCompound("SpawnData")
                            .flatMap(c -> c.getCompound("entity"))
                            .flatMap(e -> e.getString("id"))
                            .orElse("minecraft:unknown");
                } catch (RuntimeException ex) {
                    mobId = "minecraft:unknown";
                }
            }
            String path = mobId.contains(":") ? mobId.substring(mobId.indexOf(':') + 1) : mobId;

            int nearChests = 0;
            for (BlockPos c : chests) {
                if (c.getSquaredDistance(pos) <= 14 * 14) nearChests++;
            }

            String kind;
            boolean unusual = false;
            if (trial) {
                kind = "Trial Chamber";
                path = "trial";
            } else {
                switch (path) {
                    case "cave_spider" -> kind = "Mineshaft";
                    case "zombie", "skeleton", "spider" -> kind = "Dungeon";
                    case "blaze" -> kind = "Nether Fortress";
                    case "silverfish" -> kind = "Stronghold";
                    default -> {
                        kind = "Unusual";
                        unusual = true;
                    }
                }
            }

            SpawnerInfo info = new SpawnerInfo(pos, dim, prettify(path), kind, unusual, nearChests);
            if (previous != null) {
                for (SpawnerInfo old : previous) {
                    if (old.pos.equals(pos)) info.alerted = old.alerted;
                }
            }
            list.add(info);

            if (Config.notify && Config.spawnerFinder && !info.alerted) {
                info.alerted = true;
                MinecraftClient mc = MinecraftClient.getInstance();
                double dist = mc.player == null ? 0 : Math.sqrt(mc.player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                say(Text.literal(info.mob + " spawner ").formatted(info.unusual ? Formatting.RED : Formatting.AQUA)
                        .append(Text.literal("(" + info.kind + ")  ").formatted(Formatting.GRAY))
                        .append(Text.literal(pos.getX() + " " + pos.getY() + " " + pos.getZ()).formatted(Formatting.WHITE))
                        .append(Text.literal("  " + (int) dist + "m").formatted(Formatting.GRAY)), true);
            }
        }
        SPAWNERS.put(key, list);
    }

    // ---------------------------------------------------------------- queries for the UI

    public static List<ChunkInfo> susChunks(String dim) {
        List<ChunkInfo> out = new ArrayList<>();
        for (ChunkInfo c : CHUNKS.values()) {
            if (c.dimension.equals(dim) && c.score >= Config.threshold) out.add(c);
        }
        out.sort(Comparator.comparingInt((ChunkInfo c) -> c.score).reversed());
        return out;
    }

    public static List<SpawnerInfo> spawners(String dim) {
        List<SpawnerInfo> out = new ArrayList<>();
        for (List<SpawnerInfo> l : SPAWNERS.values()) {
            for (SpawnerInfo s : l) if (s.dimension.equals(dim)) out.add(s);
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
            out.sort(Comparator.comparingDouble(s -> s.pos.getSquaredDistance(px, py, pz)));
        }
        return out;
    }

    public static String currentDimension() {
        ClientWorld w = MinecraftClient.getInstance().world;
        return w == null ? "" : w.getRegistryKey().getValue().toString();
    }

    // ---------------------------------------------------------------- classification

    private static boolean interesting(BlockState state) {
        Block b = state.getBlock();
        return b == Blocks.BAMBOO || b == Blocks.BAMBOO_SAPLING || isAmethyst(b) || weight(b) > 0;
    }

    private static boolean isAmethyst(Block b) {
        return b == Blocks.AMETHYST_BLOCK || b == Blocks.BUDDING_AMETHYST
                || b == Blocks.AMETHYST_CLUSTER || b == Blocks.LARGE_AMETHYST_BUD
                || b == Blocks.MEDIUM_AMETHYST_BUD || b == Blocks.SMALL_AMETHYST_BUD;
    }

    /** How strongly a block hints at player activity. Natural structure loot (chests, rails) is weighted low. */
    private static int weight(Block b) {
        Integer cached = WEIGHT_MEMO.get(b);
        if (cached != null) return cached;
        int w = 0;
        if (b instanceof EnderChestBlock) w = 8;
        else if (b instanceof BeaconBlock) w = 10;
        else if (b instanceof ShulkerBoxBlock) w = 6;
        else if (b instanceof ObserverBlock) w = 5;
        else if (b instanceof HopperBlock) w = 4;
        else if (b instanceof BedBlock) w = 4;
        else if (b instanceof PistonBlock) w = 4;
        else if (b instanceof AbstractRedstoneGateBlock) w = 4;
        else if (b instanceof AbstractFurnaceBlock) w = 3;
        else if (b instanceof BarrelBlock) w = 3;
        else if (b instanceof AbstractSignBlock) w = 3;
        else if (b instanceof AbstractBannerBlock) w = 3;
        else if (b instanceof TntBlock) w = 3;
        else if (b instanceof DispenserBlock) w = 3;
        else if (b instanceof EnchantingTableBlock) w = 3;
        else if (b instanceof ChestBlock) w = 2;
        else if (b instanceof CraftingTableBlock) w = 2;
        WEIGHT_MEMO.put(b, w);
        return w;
    }

    // ---------------------------------------------------------------- helpers

    private static Map<String, Integer> sortByValue(Map<String, Integer> in) {
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(in.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : entries) out.put(e.getKey().replace('_', ' '), e.getValue());
        return out;
    }

    private static String prettify(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(part.substring(0, 1).toUpperCase(Locale.ROOT)).append(part.substring(1));
        }
        return sb.toString();
    }

    private static void say(Text body, boolean sound) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        Text prefix = Text.literal("[Krypton] ").formatted(Formatting.LIGHT_PURPLE);
        mc.player.sendMessage(prefix.copy().append(body), false);
        if (sound) mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.3f);
    }

    private Scanner() {}
}
