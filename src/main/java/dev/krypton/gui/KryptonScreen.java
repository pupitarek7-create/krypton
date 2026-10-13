package dev.krypton.gui;

import dev.krypton.ChunkInfo;
import dev.krypton.Config;
import dev.krypton.Scanner;
import dev.krypton.SpawnerInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/** Clean three-tab menu: Spawners, Chunks, Settings. Clicking a list row copies its coordinates. */
public class KryptonScreen extends Screen {
    private enum Tab { SPAWNERS, CHUNKS, SETTINGS }

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 214;
    private static final int PANEL_BG = 0xE0101418;
    private static final int ACCENT = 0xFFA78BFA;
    private static final int TEXT = 0xFFE5E7EB;
    private static final int MUTED = 0xFF9CA3AF;

    private static Tab tab = Tab.SPAWNERS;
    private int left, top;

    public KryptonScreen() {
        super(Text.literal("Krypton"));
    }

    @Override
    protected void init() {
        stepLabels.clear();
        left = (width - PANEL_W) / 2;
        top = (height - PANEL_H) / 2;

        int tw = (PANEL_W - 24) / 3;
        int i = 0;
        for (Tab t : Tab.values()) {
            String name = t == Tab.SPAWNERS ? "Spawners" : t == Tab.CHUNKS ? "Chunks" : "Settings";
            String label = t == tab ? "[ " + name + " ]" : name;
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                tab = t;
                clearAndInit();
            }).dimensions(left + 8 + i * (tw + 4), top + 26, tw, 18).build());
            i++;
        }

        switch (tab) {
            case SPAWNERS -> buildSpawners();
            case CHUNKS -> buildChunks();
            case SETTINGS -> buildSettings();
        }
    }

    private void buildSpawners() {
        List<SpawnerInfo> list = Scanner.spawners(Scanner.currentDimension());
        MinecraftClient mc = MinecraftClient.getInstance();
        int y = top + 52;
        int n = 0;
        for (SpawnerInfo s : list) {
            if (n++ >= 8) break;
            int d = mc.player == null ? 0
                    : (int) Math.sqrt(s.pos.getSquaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            String text = s.mob + " · " + s.kind + " · " + d + "m  "
                    + s.pos.getX() + " " + s.pos.getY() + " " + s.pos.getZ();
            String coords = s.pos.getX() + " " + s.pos.getY() + " " + s.pos.getZ();
            addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> copy(coords))
                    .dimensions(left + 8, y, PANEL_W - 16, 18).build());
            y += 20;
        }
    }

    private void buildChunks() {
        List<ChunkInfo> list = Scanner.susChunks(Scanner.currentDimension());
        int y = top + 52;
        int n = 0;
        for (ChunkInfo c : list) {
            if (n++ >= 8) break;
            String age = Config.ageOverlay ? " · " + c.age.name().toLowerCase() : "";
            String text = "Score " + c.score + age + " · " + c.summary(2);
            String coords = c.centerX() + " " + (c.anchor != null ? c.anchor.getY() : 64) + " " + c.centerZ();
            addDrawableChild(ButtonWidget.builder(Text.literal(text), b -> copy(coords))
                    .dimensions(left + 8, y, PANEL_W - 16, 18).build());
            y += 20;
        }
    }

    private void buildSettings() {
        int y = top + 52;
        int w = (PANEL_W - 20) / 2;
        toggle(left + 8, y, w, "Chunk finder", () -> Config.chunkFinder, v -> Config.chunkFinder = v);
        toggle(left + 12 + w, y, w, "Spawner finder", () -> Config.spawnerFinder, v -> Config.spawnerFinder = v);
        y += 22;
        toggle(left + 8, y, w, "Boxes", () -> Config.boxes, v -> Config.boxes = v);
        toggle(left + 12 + w, y, w, "Tracers", () -> Config.tracers, v -> Config.tracers = v);
        y += 22;
        toggle(left + 8, y, w, "Labels", () -> Config.labels, v -> Config.labels = v);
        toggle(left + 12 + w, y, w, "HUD list", () -> Config.hudList, v -> Config.hudList = v);
        y += 22;
        toggle(left + 8, y, w, "Chat alerts", () -> Config.notify, v -> Config.notify = v);
        toggle(left + 12 + w, y, w, "Old/new chunks", () -> Config.ageOverlay, v -> Config.ageOverlay = v);
        y += 26;

        step(left + 8, y, "Sus threshold", () -> Config.threshold, v -> Config.threshold = Math.max(1, Math.min(60, v)), 1);
        y += 22;
        step(left + 8, y, "ESP range", () -> Config.maxDistance, v -> Config.maxDistance = Math.max(50, Math.min(1000, v)), 50);
    }

    private void toggle(int x, int y, int w, String name, java.util.function.BooleanSupplier get,
                        java.util.function.Consumer<Boolean> set) {
        addDrawableChild(ButtonWidget.builder(label(name, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(label(name, get.getAsBoolean()));
            Config.save();
        }).dimensions(x, y, w, 18).build());
    }

    private void step(int x, int y, String name, java.util.function.IntSupplier get,
                      java.util.function.IntConsumer set, int amount) {
        int labelW = 150;
        int bw = 24;
        ButtonWidget value = ButtonWidget.builder(Text.literal(String.valueOf(get.getAsInt())), b -> {})
                .dimensions(x + labelW + bw + 2, y, 50, 18).build();
        value.active = false;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> {
            set.accept(get.getAsInt() - amount);
            value.setMessage(Text.literal(String.valueOf(get.getAsInt())));
            Config.save();
        }).dimensions(x + labelW, y, bw, 18).build());
        addDrawableChild(value);
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            set.accept(get.getAsInt() + amount);
            value.setMessage(Text.literal(String.valueOf(get.getAsInt())));
            Config.save();
        }).dimensions(x + labelW + bw + 54, y, bw, 18).build());
        stepLabels.add(new Object[]{name, x, y});
    }

    private final java.util.List<Object[]> stepLabels = new java.util.ArrayList<>();

    private static Text label(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    private void copy(String coords) {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.keyboard.setClipboard(coords);
        if (mc.player != null) mc.player.sendMessage(Text.literal("[Krypton] Copied " + coords), true);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(left, top, left + PANEL_W, top + PANEL_H, PANEL_BG);
        ctx.fill(left, top, left + PANEL_W, top + 2, ACCENT);
        ctx.drawText(textRenderer, "KRYPTON", left + 8, top + 9, TEXT, false);
        ctx.drawText(textRenderer, "click a row to copy coordinates", left + PANEL_W - 8
                - textRenderer.getWidth("click a row to copy coordinates"), top + 9, MUTED, false);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        for (Object[] l : stepLabels) {
            ctx.drawText(textRenderer, (String) l[0], (int) l[1] + 2, (int) l[2] + 5, TEXT, false);
        }
        if (children().stream().noneMatch(c -> c instanceof ButtonWidget bw && bw.getY() > top + 48)) {
            String msg = tab == Tab.SPAWNERS ? "No spawners found in loaded chunks yet." : "No suspicious chunks yet.";
            ctx.drawCenteredTextWithShadow(textRenderer, msg, left + PANEL_W / 2, top + 100, MUTED);
        }
    }
}
