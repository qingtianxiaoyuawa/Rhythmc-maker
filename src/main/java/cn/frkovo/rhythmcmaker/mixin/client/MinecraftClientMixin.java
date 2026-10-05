package cn.frkovo.rhythmcmaker.mixin.client;

import cn.frkovo.rhythmcmaker.client.EffectTrackScreen;
import cn.frkovo.rhythmcmaker.client.render.ImGuiRuntime;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Renders the editor after Minecraft presents its main framebuffer. */
@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {
    @Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
    private void rhythmcMaker$blockEditorGameplayInput(CallbackInfo callback) {
        if (MinecraftClient.getInstance().currentScreen instanceof EffectTrackScreen) callback.cancel();
    }

    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void rhythmcMaker$blockEditorAttack(CallbackInfoReturnable<Boolean> callback) {
        if (MinecraftClient.getInstance().currentScreen instanceof EffectTrackScreen) callback.setReturnValue(false);
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void rhythmcMaker$blockEditorUse(CallbackInfo callback) {
        if (MinecraftClient.getInstance().currentScreen instanceof EffectTrackScreen) callback.cancel();
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void rhythmcMaker$blockEditorBreaking(boolean breaking, CallbackInfo callback) {
        if (MinecraftClient.getInstance().currentScreen instanceof EffectTrackScreen) callback.cancel();
    }

    @Inject(
            method = "render(Z)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gl/Framebuffer;blitToScreen()V",
                    shift = At.Shift.AFTER
            )
    )
    private void rhythmcMaker$renderEffectEditor(boolean tick, CallbackInfo callback) {
        if (MinecraftClient.getInstance().currentScreen instanceof EffectTrackScreen editor) {
            editor.presentPreview(MinecraftClient.getInstance());
            ImGuiRuntime.markRenderCallback();
            ImGuiRuntime.renderPendingDrawData();
        }
    }
}
