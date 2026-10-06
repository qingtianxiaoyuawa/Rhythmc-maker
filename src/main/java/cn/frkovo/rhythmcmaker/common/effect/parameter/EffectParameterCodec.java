package cn.frkovo.rhythmcmaker.common.effect.parameter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import cn.frkovo.rhythmcmaker.common.text.EffectTextFormatter;

public final class EffectParameterCodec {
    private static final EffectParameterCodec INSTANCE = new EffectParameterCodec();

    private EffectParameterCodec() {
    }

    public static EffectParameterCodec getInstance() {
        return INSTANCE;
    }

    public JsonObject createDefaults(String eventType) {
        EffectParameterType type = EffectParameterSchema.getInstance().find(eventType);
        if (type == null) throw new IllegalArgumentException("Unknown effect type: " + eventType);
        JsonObject properties = new JsonObject();
        for (EffectParameterField field : type.fields()) {
            if (!field.optional() && field.branches().isEmpty()) properties.add(field.name(), JsonParser.parseString(field.defaultJson()));
        }
        addBranchDefaults(type, properties);
        if (eventType.equals("TEXT_DISPLAY")) properties.addProperty("id", "text-display-" + UUID.randomUUID());
        return properties;
    }

    public void addBranchDefaults(EffectParameterType type, JsonObject properties) {
        String branch = branch(properties);
        for (EffectParameterField field : type.fields()) {
            if (!field.optional() && field.active(branch) && !properties.has(field.name())) {
                properties.add(field.name(), JsonParser.parseString(field.defaultJson()));
            }
        }
    }

    public String type(JsonObject event) {
        for (String name : List.of("eventType", "effectType", "type")) {
            if (event.has(name) && event.get(name).isJsonPrimitive() && event.getAsJsonPrimitive(name).isString()) {
                String value = event.get(name).getAsString();
                if (value.equalsIgnoreCase("CHANGE_ARENA")) { event.addProperty(name, "ARENA"); return "ARENA"; }
                return value;
            }
        }
        return "";
    }

    public JsonElement read(JsonObject event, String fieldName) {
        if (event.has("properties") && event.get("properties").isJsonObject() && event.getAsJsonObject("properties").has(fieldName)) {
            return event.getAsJsonObject("properties").get(fieldName);
        }
        if (!List.of("type", "beat", "eventType", "effectType").contains(fieldName) && event.has(fieldName)) return event.get(fieldName);
        return null;
    }

    public void write(JsonObject event, String fieldName, JsonElement value) {
        JsonObject properties;
        if (!event.has("properties")) {
            properties = new JsonObject();
            event.add("properties", properties);
        } else {
            if (!event.get("properties").isJsonObject()) throw new IllegalArgumentException("properties 必须是 JSON 对象");
            properties = event.getAsJsonObject("properties");
        }
        properties.add(fieldName, value.deepCopy());
        if (!fieldName.equals("type")) event.remove(fieldName);
        EffectParameterType type = EffectParameterSchema.getInstance().find(type(event));
        if (type != null && type.eventType().equals("TEXT_DISPLAY_EFFECT") && fieldName.equals("type")) addBranchDefaults(type, properties);
    }

    public void remove(JsonObject event, String fieldName) {
        if (event.has("properties") && event.get("properties").isJsonObject()) event.getAsJsonObject("properties").remove(fieldName);
        if (!fieldName.equals("type")) event.remove(fieldName);
    }

    public String branch(JsonObject properties) {
        JsonElement type = properties.get("type");
        return type != null && type.isJsonPrimitive() && type.getAsJsonPrimitive().isString() ? type.getAsString() : "";
    }

    public EffectParameterResult parse(EffectParameterField field, String input) {
        JsonElement value;
        try {
            value = switch (field.control()) {
                case TEXT, MULTILINE_TEXT, ENUM -> new JsonPrimitive(normalizeText(field, input));
                case STRING_LIST -> {
                    JsonArray lines = new JsonArray();
                    if (!input.isEmpty()) for (String line : input.split("\\R", -1)) lines.add(EffectTextFormatter.getInstance().normalize(line));
                    yield lines;
                }
                default -> JsonParser.parseString(input);
            };
        } catch (com.google.gson.JsonParseException exception) {
            return new EffectParameterResult(null, "格式错误：" + exception.getMessage());
        }
        String error = validate(field, value);
        return new EffectParameterResult(value, error);
    }

    public String input(EffectParameterField field, JsonElement value) {
        if (value == null) return "";
        if ((field.control() == EffectParameterControl.TEXT || field.control() == EffectParameterControl.MULTILINE_TEXT
                || field.control() == EffectParameterControl.ENUM) && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) return field.control() == EffectParameterControl.ENUM ? value.getAsString() : EffectTextFormatter.getInstance().normalize(value.getAsString());
        if (field.control() == EffectParameterControl.STRING_LIST && validate(field, value).isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (JsonElement line : value.getAsJsonArray()) lines.add(EffectTextFormatter.getInstance().normalize(line.getAsString()));
            return String.join("\n", lines);
        }
        return value.toString();
    }

    private String normalizeText(EffectParameterField field, String value) {
        return field.control() == EffectParameterControl.ENUM ? value : EffectTextFormatter.getInstance().normalize(value);
    }

    public List<String> validateEvent(JsonObject event) {
        EffectParameterType type = EffectParameterSchema.getInstance().find(type(event));
        if (type == null) return List.of();
        if (event.has("properties") && !event.get("properties").isJsonObject()) return List.of("properties 必须是 JSON 对象");
        List<String> errors = new ArrayList<>();
        JsonElement typeField = read(event, "type");
        String branch = typeField != null && typeField.isJsonPrimitive() && typeField.getAsJsonPrimitive().isString() ? typeField.getAsString() : "";
        for (EffectParameterField field : type.fields()) {
            if (!field.active(branch)) continue;
            JsonElement value = read(event, field.name());
            if (value == null && field.optional()) continue;
            String error = value == null ? "缺少必填参数" : validate(field, value);
            if (!error.isEmpty()) errors.add(field.label() + "：" + error);
        }
        if (type.eventType().equals("CLEAR_EFFECT")) {
            JsonElement selectedEffects = read(event, "effects");
            if (selectedEffects != null && selectedEffects.isJsonArray() && selectedEffects.getAsJsonArray().isEmpty()) {
                errors.add("至少选择一个药水效果，或选择全部药水效果；若不清除药水效果，请删除此特效。");
            }
        }
        if (type.eventType().equals("TITLE") && errors.isEmpty()) {
            long total = read(event, "fadeIn").getAsLong() + read(event, "stay").getAsLong() + read(event, "fadeOut").getAsLong();
            if (total > Integer.MAX_VALUE) errors.add("标题淡入、停留和淡出时间之和不能超过 2147483647 毫秒");
        }
        return List.copyOf(errors);
    }

    public String validate(EffectParameterField field, JsonElement value) {
        if (value == null || value.isJsonNull()) return "不能为 null";
        return switch (field.control()) {
            case TEXT, MULTILINE_TEXT -> {
                if (!isString(value)) yield "必须是字符串";
                if ((field.name().equals("id") || field.name().equals("arena")) && value.getAsString().isBlank()) yield "不能为空";
                yield "";
            }
            case ENUM -> isString(value) && field.choices().contains(value.getAsString()) ? "" : "请从列表中选择合法值";
            case BOOLEAN -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean() ? "" : "必须是 boolean";
            case INTEGER, LONG -> number(value, field.minimum(), field.maximum(), true, field.control() == EffectParameterControl.INTEGER);
            case DECIMAL -> number(value, field.minimum(), field.maximum(), false, false);
            case VECTOR -> vector(value, 3, false);
            case RGBA -> vector(value, 4, true);
            case VECTOR_LIST, RGB_LIST -> {
                if (!value.isJsonArray()) yield "必须是二维数组";
                String error = "";
                int index = 0;
                for (JsonElement row : value.getAsJsonArray()) {
                    error = vector(row, 3, field.control() == EffectParameterControl.RGB_LIST);
                    if (!error.isEmpty()) { error = "第 " + (index + 1) + " 行：" + error; break; }
                    index++;
                }
                yield error;
            }
            case INTEGER_LIST -> {
                if (!value.isJsonArray()) yield "必须是整数数组，例如 [1,2]";
                String error = "";
                for (JsonElement item : value.getAsJsonArray()) {
                    error = number(item, field.name().equals("effects") ? 1 : 0, Integer.MAX_VALUE, true, true);
                    if (!error.isEmpty()) break;
                }
                yield error;
            }
            case STRING_LIST, NOTE_TYPES -> {
                if (!value.isJsonArray()) yield "必须是字符串数组";
                String error = "";
                for (JsonElement item : value.getAsJsonArray()) {
                    if (!isString(item)) { error = "每一项必须是字符串"; break; }
                    if (field.control() == EffectParameterControl.NOTE_TYPES && !field.choices().contains(item.getAsString())) {
                        error = "未知音符类型：" + item.getAsString();
                        break;
                    }
                }
                yield error;
            }
        };
    }

    private boolean isString(JsonElement value) {
        return value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
    }

    private String vector(JsonElement value, int size, boolean color) {
        if (!value.isJsonArray() || value.getAsJsonArray().size() != size) return "必须包含 " + size + " 个分量";
        for (JsonElement component : value.getAsJsonArray()) {
            String error = number(component, color ? 0 : -Float.MAX_VALUE, color ? 255 : Float.MAX_VALUE, color, color);
            if (!error.isEmpty()) return error;
        }
        return "";
    }

    private String number(JsonElement value, double minimum, double maximum, boolean integer, boolean int32) {
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) return "必须是数字，不能是字符串";
        double decimal = value.getAsDouble();
        if (!Double.isFinite(decimal)) return "必须是有限数字";
        if (integer) {
            try {
                BigDecimal exact = new BigDecimal(value.getAsString());
                if (int32) exact.intValueExact();
                else exact.longValueExact();
            } catch (ArithmeticException | NumberFormatException exception) {
                return int32 ? "必须是 32 位整数" : "必须是 64 位整数";
            }
        }
        if (decimal < minimum || decimal > maximum) return "超出允许范围 " + minimum + " 至 " + maximum;
        return "";
    }
}
