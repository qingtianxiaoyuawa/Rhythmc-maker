package cn.frkovo.rhythmcmaker.mixin.client;

import cn.frkovo.rhythmcmaker.client.EffectTrackScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders ImGui after Minecraft's deferred GUI state has been submitted. */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    @Inject(method = "renderBlur", at = @At("HEAD"), cancellable = true)
    private void rhythmcMaker$disableEditorBlur(CallbackInfo callback) {
        if (MinecraftClient.getInstance().currentScreen instanceof EffectTrackScreen) callback.cancel();
    }

}
