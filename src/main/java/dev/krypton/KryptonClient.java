package dev.krypton;

import dev.krypton.gui.KryptonScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class KryptonClient implements ClientModInitializer {
    public static final String ID = "krypton";
    private static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Config.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.krypton.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.create(Identifier.of(ID, "main"))));

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> Scanner.queue(chunk));
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> Scanner.unloaded(chunk));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> Scanner.clear());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            Scanner.tick(client);
            while (openKey.wasPressed()) {
                client.setScreen(new KryptonScreen());
            }
        });

        HudElementRegistry.attachElementBefore(
                VanillaHudElements.CHAT,
                Identifier.of(ID, "overlay"),
                (context, tickCounter) -> Overlay.render(context));
    }
}
