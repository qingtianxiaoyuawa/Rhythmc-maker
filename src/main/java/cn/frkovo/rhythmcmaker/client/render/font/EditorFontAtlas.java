package cn.frkovo.rhythmcmaker.client.render.font;

import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public final class EditorFontAtlas {
    private static final EditorFontAtlas INSTANCE = new EditorFontAtlas();
    private static final String FONT_RESOURCE = "/assets/rhythmc_maker/font/editor.ttf";
    private static final String REQUIRED_GLYPHS = "特效编辑器场景名称参数设置复制删除轨道背景颜色不透明度导入导出保存取消确认雨雪天气红绿蓝长按闪避线性变换（），。：；！？–●✓";
    private final short[] glyphRanges = {0x0020, (short) 0xD7FF, (short) 0xE000, (short) 0xFFFD, 0};
    private byte[] fontData;

    private EditorFontAtlas() {
    }

    public static EditorFontAtlas getInstance() {
        return INSTANCE;
    }

    public ImFont load(ImFontAtlas atlas) {
        try (InputStream input = EditorFontAtlas.class.getResourceAsStream(FONT_RESOURCE)) {
            if (input == null) throw new IllegalStateException("缺少编辑器专用字体资源：" + FONT_RESOURCE);
            fontData = input.readAllBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException("无法读取编辑器专用字体", exception);
        }
        if (fontData.length == 0) throw new IllegalStateException("编辑器专用字体为空");
        atlas.setTexDesiredWidth(2048);
        ImFontConfig config = new ImFontConfig();
        config.setName("RhythMC Maker Editor Unicode BMP");
        config.setOversampleH(1);
        config.setOversampleV(1);
        try {
            ImFont font = atlas.addFontFromMemoryTTF(fontData, 18.0f, config, glyphRanges);
            if (font.isNotValidPtr() || !atlas.build() || !font.isLoaded()) {
                throw new IllegalStateException("编辑器字体图集构建失败");
            }
            for (int codepoint : REQUIRED_GLYPHS.codePoints().toArray()) {
                if (font.findGlyphNoFallback(codepoint).isNotValidPtr()) {
                    throw new IllegalStateException("编辑器字体缺失必需字形：U+" + Integer.toHexString(codepoint));
                }
            }
            return font;
        } finally {
            config.destroy();
        }
    }

    public void release() {
        fontData = null;
    }
}
