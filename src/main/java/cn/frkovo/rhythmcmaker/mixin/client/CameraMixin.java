package cn.frkovo.rhythmcmaker.mixin.client;

import cn.frkovo.rhythmcmaker.client.RhythmcMakerClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the editor preview camera without moving the player entity. */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    protected abstract void setPos(Vec3d pos);

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void rhythmcMaker$bindEffectPreviewCamera(World world, Entity focusedEntity, boolean thirdPerson,
                                                       boolean inverseView, float tickDelta, CallbackInfo callback) {
        if (!RhythmcMakerClient.isEffectEditorCameraLocked()) return;
        setPos(RhythmcMakerClient.effectEditorCameraPosition());
        setRotation(90.0f, 90.0f);
    }
}
