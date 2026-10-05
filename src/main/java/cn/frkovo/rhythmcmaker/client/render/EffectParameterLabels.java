package cn.frkovo.rhythmcmaker.client.render;

import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterControl;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterField;
import java.util.List;

public final class EffectParameterLabels {
    private static final EffectParameterLabels INSTANCE = new EffectParameterLabels();

    private EffectParameterLabels() {
    }

    public static EffectParameterLabels getInstance() {
        return INSTANCE;
    }

    public String choice(String value) {
        String translation = switch (value) {
            case "BALL" -> "小型球状";
            case "LARGE_BALL" -> "大型球状";
            case "STAR" -> "星形";
            case "BURST" -> "爆裂状";
            case "CREEPER" -> "苦力怕形";
            case "CLEAR" -> "晴天";
            case "DOWNFALL" -> "雨雪天气";
            case "TEXT" -> "修改文本";
            case "SHADOW" -> "文字阴影";
            case "OPACITY" -> "文字不透明度";
            case "BACKGROUND_COLOR" -> "背景颜色";
            case "LINEAR_TRANSFORMATION" -> "线性变换（位置、旋转、缩放）";
            case "GLOWING" -> "发光";
            case "TAP" -> "点击音符";
            case "LOOK" -> "视角音符";
            case "HOLD" -> "长按音符";
            case "DODGE" -> "闪避音符";
            case "BLACK" -> "黑色";
            case "DARK_BLUE" -> "深蓝色";
            case "DARK_GREEN" -> "深绿色";
            case "DARK_AQUA" -> "深青色";
            case "DARK_RED" -> "深红色";
            case "DARK_PURPLE" -> "深紫色";
            case "GOLD" -> "金色";
            case "GRAY" -> "灰色";
            case "DARK_GRAY" -> "深灰色";
            case "BLUE" -> "蓝色";
            case "GREEN" -> "绿色";
            case "AQUA" -> "青色";
            case "RED" -> "红色";
            case "LIGHT_PURPLE" -> "浅紫色";
            case "YELLOW" -> "黄色";
            case "WHITE" -> "白色";
            default -> "";
        };
        return translation.isEmpty() ? value : translation + "（" + value + "）";
    }

    public String component(EffectParameterField field, int index) {
        if (field.control() == EffectParameterControl.RGB_LIST || field.control() == EffectParameterControl.RGBA) {
            return List.of("红（R）", "绿（G）", "蓝（B）", "不透明度（A）").get(index);
        }
        return List.of("X 轴", "Y 轴", "Z 轴").get(index);
    }
}
