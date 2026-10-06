package cn.frkovo.rhythmcmaker.mixin.client;

import cn.frkovo.rhythmcmaker.client.EffectTrackScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
abstract class InGameHudMixin {
    @Inject(method = "renderHotbar", at = @At("HEAD"), cancellable = true)
    private void rhythmcMaker$hideEffectEditorHotbar(DrawContext context, float tickDelta, CallbackInfo callback) {
        if (MinecraftClient.getInstance().currentScreen instanceof EffectTrackScreen) callback.cancel();
    }
}
