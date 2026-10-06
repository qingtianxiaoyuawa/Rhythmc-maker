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

    public String potion(int id) {
        String name = switch (id) {
            case 1 -> "速度";
            case 2 -> "缓慢";
            case 3 -> "急迫";
            case 4 -> "挖掘疲劳";
            case 5 -> "力量";
            case 6 -> "瞬间治疗";
            case 7 -> "瞬间伤害";
            case 8 -> "跳跃提升";
            case 9 -> "反胃";
            case 10 -> "生命恢复";
            case 11 -> "抗性提升";
            case 12 -> "防火";
            case 13 -> "水下呼吸";
            case 14 -> "隐身";
            case 15 -> "失明";
            case 16 -> "夜视";
            case 17 -> "饥饿";
            case 18 -> "虚弱";
            case 19 -> "中毒";
            case 20 -> "凋零";
            case 21 -> "生命提升";
            case 22 -> "吸收";
            case 23 -> "饱和";
            case 24 -> "发光";
            case 25 -> "漂浮";
            case 26 -> "幸运";
            case 27 -> "霉运";
            case 28 -> "缓降";
            case 29 -> "潮涌能量";
            case 30 -> "海豚的恩惠";
            case 31 -> "不祥之兆";
            case 32 -> "村庄英雄";
            case 33 -> "黑暗";
            default -> "未知效果";
        };
        return name + "（" + id + "）";
    }
}
