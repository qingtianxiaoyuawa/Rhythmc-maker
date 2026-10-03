package cn.frkovo.rhythmcmaker.mixin.client;

import cn.frkovo.rhythmcmaker.client.EffectTrackScreen;
import cn.frkovo.rhythmcmaker.client.render.ImGuiRuntime;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders the editor after Minecraft presents its main framebuffer. */
@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
    @Inject(
            method = "render(Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gl/Framebuffer;blitToScreen()V",
                    shift = At.Shift.AFTER
            )
    )
    private void rhythmcMaker$renderEffectEditor(boolean tick, CallbackInfo callback) {
        if (MinecraftClient.getInstance().currentScreen instanceof EffectTrackScreen) {
            ImGuiRuntime.markRenderCallback();
            ImGuiRuntime.renderPendingDrawData();
        }
    }
}
