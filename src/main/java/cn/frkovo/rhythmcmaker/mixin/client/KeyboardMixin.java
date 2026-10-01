package cn.frkovo.rhythmcmaker.mixin.client;

import cn.frkovo.rhythmcmaker.client.ImGuiRuntimeAccess;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.CharInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
abstract class KeyboardMixin {
    @Inject(method = \u0022onChar(JLnet/minecraft/client/input/CharInput;)V\u0022, at = @At(\u0022HEAD\u0022), cancellable = true)
    private void rhythmcMaker(long window, CharInput input, CallbackInfo callback) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof BaseOwoScreen<?>) || !input.isValidChar()) return;
        ImGuiRuntimeAccess.character(input.codepoint());
        callback.cancel();
    }
}
