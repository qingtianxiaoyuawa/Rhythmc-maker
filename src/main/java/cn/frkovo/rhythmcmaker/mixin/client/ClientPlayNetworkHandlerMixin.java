package cn.frkovo.rhythmcmaker.mixin.client;

import cn.frkovo.rhythmcmaker.client.RhythmcMakerClient;
import cn.frkovo.rhythmcmaker.client.NoteEditScreen;
import net.minecraft.network.packet.s2c.play.OverlayMessageS2CPacket;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onOverlayMessage(Lnet/minecraft/network/packet/s2c/play/OverlayMessageS2CPacket;)V", at = @At("TAIL"))
    private void rhythmcMaker$noteOperationStatus(OverlayMessageS2CPacket packet, CallbackInfo callback) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof NoteEditScreen screen)) return;
        String value = packet.text().getString();
        if (value.startsWith("音符已更新")) screen.showServerStatus(value, true);
        else if (value.startsWith("音符类型已改为 ")) screen.showServerStatus(value, true);
    }

    @Inject(method = "sendChatCommand(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void rhythmcMaker(String command, CallbackInfo callback) {
        if (RhythmcMakerClient.isSceneOperationBusy()) callback.cancel();
    }
}
