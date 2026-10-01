package io.wispforest.owo.ui.component;

import imgui.ImGui;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.util.Identifier;

public final class TextureComponent extends UiComponent {
    private final Identifier texture;
    private final int u;
    private final int v;
    private final int regionWidth;
    private final int regionHeight;
    private final int textureWidth;
    private final int textureHeight;

    public TextureComponent(Identifier texture) {
        this(texture, 0, 0, 1, 1, 1, 1);
    }

    public TextureComponent(Identifier texture, int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        this.texture = texture;
        this.u = u;
        this.v = v;
        this.regionWidth = Math.max(1, regionWidth);
        this.regionHeight = Math.max(1, regionHeight);
        this.textureWidth = Math.max(1, textureWidth);
        this.textureHeight = Math.max(1, textureHeight);
    }

    @Override
    public void render() {
        float width = horizontalSizing.kind() == io.wispforest.owo.ui.core.Sizing.Kind.FIXED
                ? horizontalSizing.value() : regionWidth;
        float height = verticalSizing.kind() == io.wispforest.owo.ui.core.Sizing.Kind.FIXED
                ? verticalSizing.value() : regionHeight;
        ImGui.beginGroup();
        AbstractTexture loaded = MinecraftClient.getInstance().getTextureManager().getTexture(texture);
        if (loaded.getGlTexture() instanceof GlTexture glTexture && !glTexture.isClosed()) {
            float u0 = (float) u / textureWidth;
            float v0 = (float) v / textureHeight;
            float u1 = (float) (u + regionWidth) / textureWidth;
            float v1 = (float) (v + regionHeight) / textureHeight;
            ImGui.image(glTexture.getGlId(), width, height, u0, v0, u1, v1);
        } else {
            ImGui.dummy(width, height);
            ImGui.textDisabled("纹理不可用：" + texture);
        }
        ImGui.endGroup();
    }
}