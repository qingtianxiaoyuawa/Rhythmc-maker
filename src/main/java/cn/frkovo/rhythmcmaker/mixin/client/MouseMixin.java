package cn.frkovo.rhythmcmaker.mixin.client;

import cn.frkovo.rhythmcmaker.client.RhythmcMakerClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
abstract class MouseMixin {
    @Inject(method = "onMouseScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void rhythmcMaker(long window, double horizontal, double vertical, CallbackInfo callback) {
        if (RhythmcMakerClient.consumePlaybackStartScroll(vertical)) callback.cancel();
    }
}
