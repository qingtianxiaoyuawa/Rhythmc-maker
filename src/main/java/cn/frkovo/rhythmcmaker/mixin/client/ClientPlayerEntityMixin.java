package cn.frkovo.rhythmcmaker.mixin.client;

import cn.frkovo.rhythmcmaker.client.RhythmcMakerClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerEntity.class)
abstract class ClientPlayerEntityMixin {
    @Inject(method = "dropSelectedItem(Z)Z", at = @At("HEAD"), cancellable = true)
    private void rhythmcMaker$toggleHoldGroup(boolean entireStack, CallbackInfoReturnable<Boolean> callback) {
        if (RhythmcMakerClient.interceptNoteItemDrop((ClientPlayerEntity) (Object) this)) callback.setReturnValue(true);
    }
}
