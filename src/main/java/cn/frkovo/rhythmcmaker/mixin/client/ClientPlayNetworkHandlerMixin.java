package cn.frkovo.rhythmcmaker.mixin.client;

import cn.frkovo.rhythmcmaker.client.RhythmcMakerClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "sendChatCommand(Ljava/lang/String;)V", at = @At("HEAD"), cancellable = true)
    private void rhythmcMaker(String command, CallbackInfo callback) {
        if (RhythmcMakerClient.isSceneOperationBusy()) callback.cancel();
    }
}
