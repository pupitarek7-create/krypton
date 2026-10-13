package dev.krypton;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Tiny persisted settings store (config/krypton.properties). */
public final class Config {
    public static boolean chunkFinder = true;
    public static boolean spawnerFinder = true;
    public static boolean boxes = true;
    public static boolean tracers = true;
    public static boolean labels = true;
    public static boolean ageOverlay = false;
    public static boolean notify = true;
    public static boolean hudList = true;
    /** Minimum score for a chunk to count as suspicious. */
    public static int threshold = 8;
    /** Max ESP distance in blocks. */
    public static int maxDistance = 400;

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("krypton.properties");
    }

    public static void load() {
        Path f = file();
        if (!Files.exists(f)) return;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
        } catch (IOException e) {
            return;
        }
        chunkFinder = bool(p, "chunkFinder", chunkFinder);
        spawnerFinder = bool(p, "spawnerFinder", spawnerFinder);
        boxes = bool(p, "boxes", boxes);
        tracers = bool(p, "tracers", tracers);
        labels = bool(p, "labels", labels);
        ageOverlay = bool(p, "ageOverlay", ageOverlay);
        notify = bool(p, "notify", notify);
        hudList = bool(p, "hudList", hudList);
        threshold = num(p, "threshold", threshold);
        maxDistance = num(p, "maxDistance", maxDistance);
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("chunkFinder", String.valueOf(chunkFinder));
        p.setProperty("spawnerFinder", String.valueOf(spawnerFinder));
        p.setProperty("boxes", String.valueOf(boxes));
        p.setProperty("tracers", String.valueOf(tracers));
        p.setProperty("labels", String.valueOf(labels));
        p.setProperty("ageOverlay", String.valueOf(ageOverlay));
        p.setProperty("notify", String.valueOf(notify));
        p.setProperty("hudList", String.valueOf(hudList));
        p.setProperty("threshold", String.valueOf(threshold));
        p.setProperty("maxDistance", String.valueOf(maxDistance));
        try (OutputStream out = Files.newOutputStream(file())) {
            p.store(out, "Krypton settings");
        } catch (IOException ignored) {
        }
    }

    private static boolean bool(Properties p, String k, boolean d) {
        String v = p.getProperty(k);
        return v == null ? d : Boolean.parseBoolean(v);
    }

    private static int num(Properties p, String k, int d) {
        try {
            return Integer.parseInt(p.getProperty(k, String.valueOf(d)));
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private Config() {}
}
