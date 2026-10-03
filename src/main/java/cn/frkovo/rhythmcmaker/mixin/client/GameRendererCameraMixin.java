package cn.frkovo.rhythmcmaker.mixin.client;

import cn.frkovo.rhythmcmaker.client.RhythmcMakerClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the preview FOV stable while the virtual editor camera is active. */
@Mixin(GameRenderer.class)
abstract class GameRendererCameraMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void rhythmcMaker$keepEffectPreviewFov(Camera camera, float tickDelta, boolean changingFov,
                                                   CallbackInfoReturnable<Float> callback) {
        if (RhythmcMakerClient.isEffectEditorCameraLocked()) {
            callback.setReturnValue(RhythmcMakerClient.effectEditorCameraFov(callback.getReturnValueF()));
        }
    }
}
