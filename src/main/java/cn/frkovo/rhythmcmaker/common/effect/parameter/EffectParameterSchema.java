package cn.frkovo.rhythmcmaker.common.effect.parameter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import static cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterControl.*;

public final class EffectParameterSchema {
    private static final EffectParameterSchema INSTANCE = new EffectParameterSchema();
    private final List<EffectParameterType> types = new ArrayList<>();

    private EffectParameterSchema() {
        add("HOLOGRAM", "全息文字", true,
                vector("location", "位置偏移 [x,y,z]（格）", "[0,2,0]", "相对播放中心 (200.5, 66, -1) 的偏移，不是绝对世界坐标。"),
                field("contents", "文字内容（每行一条）", STRING_LIST, "[\"全息文字\"]", "保存为字符串数组，每个条目可包含多行文字。"),
                optional(field("id", "全息文字 ID", TEXT, "\"hologram-1\"", "不启用时由服务端生成 ID；删除时需使用同一 ID。")), duration());
        add("REMOVE_HOLOGRAM", "移除全息文字", true, id("hologram-1"));
        add("TITLE", "标题", true,
                field("title", "主标题", MULTILINE_TEXT, "\"标题\"", "支持服务端支持的文本格式。"),
                field("subtitle", "副标题", MULTILINE_TEXT, "\"\"", "可留空。"),
                number("fadeIn", "淡入时间（毫秒）", INTEGER, "500", 0, Integer.MAX_VALUE),
                number("stay", "停留时间（毫秒）", INTEGER, "2000", 0, Integer.MAX_VALUE),
                number("fadeOut", "淡出时间（毫秒）", INTEGER, "500", 0, Integer.MAX_VALUE));
        add("FIREWORK", "烟花", true,
                field("locations", "发射位置列表 [x,y,z]（格）", VECTOR_LIST, "[[0,2,0]]", "每行一个相对游戏中心的偏移。"),
                field("colors", "初始颜色列表 [r,g,b]", RGB_LIST, "[[255,255,255]]", "Reborn 官方 RGB 颜色；每个分量必须是 0–255 的整数，不使用透明度或十六进制。"),
                field("fadeColors", "渐变颜色列表 [r,g,b]", RGB_LIST, "[]", "Reborn 官方 RGB 颜色；允许空列表，不使用透明度或十六进制。"),
                enumeration("type", "烟花形状", "BALL", List.of("BALL", "LARGE_BALL", "STAR", "BURST", "CREEPER")),
                field("flicker", "闪烁", BOOLEAN, "false", ""), field("trail", "拖尾", BOOLEAN, "true", ""));
        add("TIME", "世界时间", true,
                number("time", "世界时间（游戏刻）", LONG, "6000", 0, Long.MAX_VALUE), duration());
        add("EFFECT", "药水效果", true,
                number("effectId", "药水效果 ID（Bukkit 数字）", INTEGER, "1", 1, Integer.MAX_VALUE),
                number("amplifier", "效果等级（0 为一级）", INTEGER, "0", 0, Integer.MAX_VALUE), duration());
        add("CLEAR_EFFECT", "清除药水效果", true,
                optional(field("effects", "要清除的药水 ID 列表", INTEGER_LIST, "[]", "不启用此字段时清除全部；启用后只清除数组中的 ID，空数组不清除任何效果。")));
        add("WEATHER", "天气", true, enumeration("weather", "天气", "CLEAR", List.of("DOWNFALL", "CLEAR")));
        add("ARENA", "切换场景", true, field("arena", "场景名称", TEXT, "\"arena\"", "使用已导入或保存的场景名称。"));
        add("HIDE_NOTES", "隐藏音符", true,
                optional(choices(field("noteTypes", "隐藏的音符类型", NOTE_TYPES, "[]", "点击 / 视角 / 长按 / 闪避音符；空数组恢复显示全部类型。"), List.of("TAP", "LOOK", "HOLD", "DODGE"))),
                optional(field("tracks", "隐藏的轨道 ID", INTEGER_LIST, "[]", "填数字轨道 ID；空数组恢复显示全部轨道。")));
        add("GLOW_COLOR", "发光颜色", true, enumeration("color", "Minecraft 颜色枚举", "WHITE", List.of(
                "BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA", "DARK_RED", "DARK_PURPLE", "GOLD", "GRAY",
                "DARK_GRAY", "BLUE", "GREEN", "AQUA", "RED", "LIGHT_PURPLE", "YELLOW", "WHITE")));
        add("MESSAGE", "聊天消息", true, field("contents", "消息内容（每行一条）", STRING_LIST, "[\"消息\"]", "保存为字符串数组，每个条目可包含多行文字。"));
        add("TEXT_DISPLAY", "文本展示实体", true,
                vector("position", "位置偏移 [x,y,z]（格）", "[0,0,0]", "相对固定播放中心 (200.5, 66, -1) 的偏移；不填写绝对世界坐标。"),
                vector("rotation", "旋转 [x,y,z]（弧度）", "[0,0,0]", "绕 X、Y、Z 轴旋转，单位为弧度，不是角度。"),
                vector("scale", "缩放 [x,y,z]", "[1,1,1]", "每个坐标轴独立缩放。"),
                field("text", "文本", MULTILINE_TEXT, "\"文本\"", ""), id("text-display-1"));
        add("TEXT_DISPLAY_EFFECT", "修改文本展示实体", true,
                id("text-display-1"), enumeration("type", "修改类型", "TEXT", List.of(
                        "TEXT", "SHADOW", "OPACITY", "BACKGROUND_COLOR", "LINEAR_TRANSFORMATION", "GLOWING")),
                branch(field("text", "文本", MULTILINE_TEXT, "\"文本\"", ""), "TEXT"),
                branch(field("shadowed", "文字阴影", BOOLEAN, "true", ""), "SHADOW"),
                branch(number("targetOpacity", "目标不透明度（0–255）", DECIMAL, "255", 0, 255), "OPACITY"),
                branch(field("color", "背景颜色 [r,g,b,a]", RGBA, "[0,0,0,128]", "Reborn 官方 RGBA 数组；四个分量均为 0–255 的整数，不是 RGB、ARGB 数字或十六进制。"), "BACKGROUND_COLOR"),
                branch(vector("position", "位置偏移 [x,y,z]（格）", "[0,0,0]", ""), "LINEAR_TRANSFORMATION"),
                branch(vector("rotation", "旋转 [x,y,z]（弧度）", "[0,0,0]", ""), "LINEAR_TRANSFORMATION"),
                branch(vector("scale", "缩放 [x,y,z]", "[1,1,1]", ""), "LINEAR_TRANSFORMATION"));
        add("TEXT_DISPLAY_SYNC_TRACK", "文本展示实体跟随轨道", true,
                id("text-display-1"), number("track", "跟随的轨道 ID", INTEGER, "0", 0, Integer.MAX_VALUE), duration());
        add("TEXT_DISPLAY_DESYNC_TRACK", "取消文本展示实体跟随", true, id("text-display-1"));
        add("TEXT_DISPLAY_REMOVE", "移除文本展示实体", true, id("text-display-1"));
    }

    public static EffectParameterSchema getInstance() {
        return INSTANCE;
    }

    public List<EffectParameterType> types() {
        return List.copyOf(types);
    }

    public EffectParameterType find(String eventType) {
        if (eventType == null) return null;
        String normalized = eventType.toUpperCase(Locale.ROOT);
        for (EffectParameterType type : types) if (type.eventType().equals(normalized)) return type;
        return null;
    }

    private void add(String eventType, String displayName, boolean official, EffectParameterField... fields) {
        types.add(new EffectParameterType(eventType, displayName, official, List.of(fields)));
    }

    private EffectParameterField field(String name, String label, EffectParameterControl control, String defaultJson, String help) {
        return new EffectParameterField(name, label, control, false, defaultJson, help, List.of(), List.of(), -Double.MAX_VALUE, Double.MAX_VALUE);
    }

    private EffectParameterField number(String name, String label, EffectParameterControl control, String defaultJson, double minimum, double maximum) {
        return new EffectParameterField(name, label, control, false, defaultJson, "", List.of(), List.of(), minimum, maximum);
    }

    private EffectParameterField enumeration(String name, String label, String defaultValue, List<String> options) {
        return choices(field(name, label, ENUM, "\"" + defaultValue + "\"", ""), options);
    }

    private EffectParameterField choices(EffectParameterField field, List<String> choices) {
        return new EffectParameterField(field.name(), field.label(), field.control(), field.optional(), field.defaultJson(),
                field.help(), choices, field.branches(), field.minimum(), field.maximum());
    }

    private EffectParameterField optional(EffectParameterField field) {
        return new EffectParameterField(field.name(), field.label(), field.control(), true, field.defaultJson(),
                field.help(), field.choices(), field.branches(), field.minimum(), field.maximum());
    }

    private EffectParameterField branch(EffectParameterField field, String branch) {
        return new EffectParameterField(field.name(), field.label(), field.control(), field.optional(), field.defaultJson(),
                field.help(), field.choices(), List.of(branch), field.minimum(), field.maximum());
    }

    private EffectParameterField vector(String name, String label, String value, String help) {
        return field(name, label, VECTOR, value, help);
    }

    private EffectParameterField duration() {
        return optional(number("duration", "持续时间（毫秒，64 位整数）", LONG, "31536000000", 0, Long.MAX_VALUE));
    }

    private EffectParameterField id(String defaultValue) {
        return field("id", "目标 ID", TEXT, "\"" + defaultValue + "\"", "必须与创建对应展示实体时填写的 ID 一致。");
    }
}
