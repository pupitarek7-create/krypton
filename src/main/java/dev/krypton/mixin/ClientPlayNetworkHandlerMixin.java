package dev.krypton.mixin;

import dev.krypton.Scanner;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds fluid-flow block updates to the old/new chunk heuristic. */
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onBlockUpdate", at = @At("HEAD"))
    private void krypton$onBlockUpdate(BlockUpdateS2CPacket packet, CallbackInfo ci) {
        Scanner.onBlockPacket(packet.getPos(), packet.getState());
    }

    @Inject(method = "onChunkDeltaUpdate", at = @At("HEAD"))
    private void krypton$onDelta(ChunkDeltaUpdateS2CPacket packet, CallbackInfo ci) {
        packet.visitUpdates((pos, state) -> Scanner.onBlockPacket(pos.toImmutable(), state));
    }
}
