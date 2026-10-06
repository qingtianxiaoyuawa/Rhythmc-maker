package cn.frkovo.rhythmcmaker.client.render;

import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterCodec;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterControl;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterField;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterResult;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterSchema;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterType;
import cn.frkovo.rhythmcmaker.common.text.EffectTextFormatter;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.type.ImBoolean;
import imgui.type.ImDouble;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.List;

public final class EffectParameterEditor {
    private final EffectParameterCodec codec = EffectParameterCodec.getInstance();
    private final EffectParameterLabels labels = EffectParameterLabels.getInstance();
    private final List<EventForm> forms = new ArrayList<>();
    private final EffectTextFormatter textFormatter = EffectTextFormatter.getInstance();
    private boolean changed;

    public boolean render(JsonObject event) {
        changed = false;
        EffectParameterType type = EffectParameterSchema.getInstance().find(codec.type(event));
        if (type == null) return false;
        EventForm form = form(event);
        if (!type.official()) ImGui.textWrapped(type.eventType().equals("ACTIONBAR")
                ? "Maker 本地扩展：可编辑和保存，但不能直接导出为官方 3.0 特效。导出前请改为 TITLE 或 MESSAGE。"
                : "旧 Maker 名称；导出时转换为官方 ARENA。");
        if (ImGui.button("转换旧草稿字段（不覆盖已填写的官方参数）")) {
            event.add("properties", cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterMigration.getInstance().convert(event));
            form.fields.clear();
            form.rawProperties = null;
            form.rawError = "";
            form.rawPending = false;
            changed = true;
        }
        ImGui.textWrapped("只转换明确对应的旧字段；缺失或无法转换的参数会显示错误，请填写后保存。");
        if (event.has("properties") && !event.get("properties").isJsonObject()) {
            ImGui.textColored(0xFF7777FF, "properties 格式错误，请在下方 JSON 编辑器修复。");
        } else {
            JsonElement branchValue = codec.read(event, "type");
            String branch = branchValue != null && branchValue.isJsonPrimitive() && branchValue.getAsJsonPrimitive().isString()
                    ? branchValue.getAsString() : "";
            for (EffectParameterField field : type.fields()) {
                if (!field.active(branch)) continue;
                ImGui.pushID(field.name());
                renderField(form, field);
                ImGui.popID();
                ImGui.separator();
            }
        }
        renderRawProperties(form);
        for (String error : codec.validateEvent(event)) ImGui.textColored(0xFF7777FF, error);
        return changed;
    }

    public List<String> errors(List<JsonObject> events) {
        forms.removeIf(form -> events.stream().noneMatch(event -> event == form.event));
        List<String> errors = new ArrayList<>();
        for (int index = 0; index < events.size(); index++) {
            JsonObject event = events.get(index);
            for (String error : codec.validateEvent(event)) errors.add("特效 #" + (index + 1) + "：" + error);
            for (EventForm form : forms) {
                if (form.event != event) continue;
                JsonElement branchValue = codec.read(event, "type");
                String branch = branchValue != null && branchValue.isJsonPrimitive() && branchValue.getAsJsonPrimitive().isString()
                        ? branchValue.getAsString() : "";
                for (FieldState state : form.fields) {
                    if (!state.error.isEmpty() && state.definition.active(branch) && codec.read(event, state.definition.name()) != null) {
                        errors.add("特效 #" + (index + 1) + " " + state.definition.label() + "：" + state.error);
                    }
                }
                if (!form.rawError.isEmpty()) errors.add("特效 #" + (index + 1) + "：" + form.rawError);
                if (form.rawPending) errors.add("特效 #" + (index + 1) + "：完整 JSON 尚未应用，请应用或重新读取已应用参数");
            }
        }
        return List.copyOf(errors);
    }

    public void clear() {
        forms.clear();
    }

    private EventForm form(JsonObject event) {
        for (EventForm form : forms) if (form.event == event) return form;
        EventForm form = new EventForm(event);
        forms.add(form);
        return form;
    }

    private FieldState state(EventForm form, EffectParameterField field) {
        JsonElement actual = codec.read(form.event, field.name());
        for (FieldState state : form.fields) {
            if (!state.definition.name().equals(field.name())) continue;
            if (state.error.isEmpty() && !java.util.Objects.equals(state.committed, actual)) state.load(actual, codec);
            return state;
        }
        FieldState state = new FieldState(field);
        state.load(actual, codec);
        form.fields.add(state);
        return state;
    }

    private void renderField(EventForm form, EffectParameterField field) {
        JsonElement actual = codec.read(form.event, field.name());
        FieldState state = state(form, field);
        ImGui.textWrapped(field.label() + "  ·  " + field.name());
        if (!field.help().isBlank()) ImGui.textWrapped(field.help());
        if (field.optional()) {
            ImBoolean enabled = new ImBoolean(actual != null);
            if (ImGui.checkbox("启用此可选参数", enabled)) {
                if (enabled.get()) codec.write(form.event, field.name(), JsonParser.parseString(field.defaultJson()));
                else codec.remove(form.event, field.name());
                state.load(codec.read(form.event, field.name()), codec);
                changed = true;
                actual = codec.read(form.event, field.name());
            }
            if (actual == null) {
                if (field.name().equals("duration")) ImGui.textDisabled("省略时使用官方默认值：31536000000 毫秒。");
                return;
            }
        } else if (actual == null) {
            ImGui.textColored(0xFF7777FF, "缺少必填参数，不会静默写入默认值。");
            if (ImGui.button("添加该参数的默认值")) {
                codec.write(form.event, field.name(), JsonParser.parseString(field.defaultJson()));
                state.load(codec.read(form.event, field.name()), codec);
                changed = true;
            }
            return;
        }
        ImGui.setNextItemWidth(-1);
        if (!shapeMatches(field.control(), state.value)) {
            if (ImGui.inputTextMultiline("##repair-value", state.repair, -1, 70, ImGuiInputTextFlags.CallbackResize)) {
                commitJson(form, state, state.repair.get());
            }
        } else {
            switch (field.control()) {
                case TEXT, INTEGER, LONG, DECIMAL, INTEGER_LIST -> {
                    if (ImGui.inputText("##value", state.input, ImGuiInputTextFlags.CallbackResize)) commitInput(form, state);
                }
                case MULTILINE_TEXT -> {
                    if (ImGui.inputTextMultiline("##value", state.input, -1, 80, ImGuiInputTextFlags.CallbackResize)) commitInput(form, state);
                }
                case ENUM -> renderEnum(form, state);
                case BOOLEAN -> {
                    ImBoolean value = new ImBoolean(state.value.getAsBoolean());
                    if (ImGui.checkbox("开启##value", value)) commitValue(form, state, new JsonPrimitive(value.get()));
                }
                case VECTOR, RGBA -> renderVector(form, state, state.value.getAsJsonArray(), -1);
                case VECTOR_LIST, RGB_LIST -> renderVectorList(form, state);
                case STRING_LIST -> renderStringList(form, state);
                case NOTE_TYPES -> renderNoteTypes(form, state);
            }
        }
        if (!state.error.isEmpty()) ImGui.textColored(0xFF7777FF, state.error);
        if (field.control() == EffectParameterControl.TEXT || field.control() == EffectParameterControl.MULTILINE_TEXT
                || field.control() == EffectParameterControl.STRING_LIST) renderStylePreview(state);
        if (ImGui.smallButton("恢复该参数默认值")) {
            commitValue(form, state, JsonParser.parseString(field.defaultJson()));
        }
    }

    private void renderStylePreview(FieldState state) {
        ImGui.textDisabled("文字样式预览");
        String value = state.value == null ? "" : state.value.isJsonArray()
                ? state.value.getAsJsonArray().toString() : state.value.getAsString();
        List<EffectTextFormatter.PreviewSegment> segments = textFormatter.preview(value, 0xFFFFFF);
        if (segments.isEmpty()) {
            ImGui.textDisabled("（空）");
            return;
        }
        for (EffectTextFormatter.PreviewSegment segment : segments) {
            ImGui.sameLine(0.0f, 0.0f);
            String suffix = segment.obfuscated() ? " ▒" : "";
            ImGui.textColored(0xFF000000 | segment.color(), segment.text() + suffix);
        }
    }

    private boolean shapeMatches(EffectParameterControl control, JsonElement value) {
        if (value == null || value.isJsonNull()) return false;
        return switch (control) {
            case TEXT, MULTILINE_TEXT, ENUM -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
            case INTEGER, LONG, DECIMAL -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber();
            case BOOLEAN -> value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
            case VECTOR, RGBA -> numericVector(value, control == EffectParameterControl.RGBA ? 4 : 3);
            case VECTOR_LIST, RGB_LIST -> value.isJsonArray() && value.getAsJsonArray().asList().stream().allMatch(row -> numericVector(row, 3));
            case STRING_LIST, NOTE_TYPES -> value.isJsonArray() && value.getAsJsonArray().asList().stream()
                    .allMatch(item -> item.isJsonPrimitive() && item.getAsJsonPrimitive().isString());
            case INTEGER_LIST -> value.isJsonArray();
        };
    }

    private boolean numericVector(JsonElement value, int size) {
        return value.isJsonArray() && value.getAsJsonArray().size() == size && value.getAsJsonArray().asList().stream()
                .allMatch(component -> component.isJsonPrimitive() && component.getAsJsonPrimitive().isNumber()
                        && Double.isFinite(component.getAsDouble()));
    }

    private void renderEnum(EventForm form, FieldState state) {
        if (ImGui.beginCombo("##value", labels.choice(state.value.getAsString()))) {
            for (String choice : state.definition.choices()) {
                if (ImGui.selectable(labels.choice(choice) + "##" + choice, choice.equals(state.value.getAsString()))) commitValue(form, state, new JsonPrimitive(choice));
            }
            ImGui.endCombo();
        }
    }

    private void renderVector(EventForm form, FieldState state, JsonArray row, int rowIndex) {
        float width = Math.max(32, (ImGui.getContentRegionAvailX() - 12) / row.size());
        JsonArray edited = row.deepCopy();
        boolean editedValue = false;
        for (int component = 0; component < row.size(); component++) {
            if (component > 0) ImGui.sameLine();
            ImGui.beginGroup();
            ImGui.pushTextWrapPos(ImGui.getCursorPosX() + width);
            ImGui.textWrapped(labels.component(state.definition, component));
            ImGui.popTextWrapPos();
            ImGui.setNextItemWidth(width);
            ImDouble value = new ImDouble(row.get(component).getAsDouble());
            if (ImGui.inputDouble("##component-" + rowIndex + "-" + component, value, 0, 0, "%.9g")) {
                edited.set(component, new JsonPrimitive(value.get()));
                editedValue = true;
            }
            ImGui.endGroup();
        }
        if (editedValue) {
            if (rowIndex < 0) commitValue(form, state, edited);
            else {
                JsonArray values = state.value.getAsJsonArray().deepCopy();
                values.set(rowIndex, edited);
                commitValue(form, state, values);
            }
        }
    }

    private void renderVectorList(EventForm form, FieldState state) {
        JsonArray values = state.value.getAsJsonArray();
        int removeIndex = -1;
        for (int index = 0; index < values.size(); index++) {
            ImGui.pushID(index);
            ImGui.text("第 " + (index + 1) + " 项");
            renderVector(form, state, values.get(index).getAsJsonArray(), index);
            if (ImGui.smallButton("删除此项")) removeIndex = index;
            ImGui.popID();
        }
        if (removeIndex >= 0) {
            JsonArray edited = state.value.getAsJsonArray().deepCopy();
            edited.remove(removeIndex);
            commitValue(form, state, edited);
        }
        if (ImGui.button("新增一项")) {
            JsonArray edited = state.value.getAsJsonArray().deepCopy();
            edited.add(JsonParser.parseString(state.definition.control() == EffectParameterControl.RGB_LIST ? "[255,255,255]" : "[0,0,0]"));
            commitValue(form, state, edited);
        }
    }

    private void renderStringList(EventForm form, FieldState state) {
        JsonArray values = state.value.getAsJsonArray();
        int removeIndex = -1;
        for (int index = 0; index < values.size(); index++) {
            ImGui.pushID(index);
            ImString text = state.rowText(index, values.get(index).getAsString());
            if (ImGui.inputTextMultiline("##line", text, -1, 54, ImGuiInputTextFlags.CallbackResize)) {
                JsonArray edited = state.value.getAsJsonArray().deepCopy();
                edited.set(index, new JsonPrimitive(text.get()));
                commitValue(form, state, edited);
            }
            if (ImGui.smallButton("删除此条")) removeIndex = index;
            ImGui.popID();
        }
        if (removeIndex >= 0) {
            JsonArray edited = state.value.getAsJsonArray().deepCopy();
            edited.remove(removeIndex);
            state.rowTexts.clear();
            commitValue(form, state, edited);
        }
        if (ImGui.button("新增文字条目")) {
            JsonArray edited = state.value.getAsJsonArray().deepCopy();
            edited.add("");
            commitValue(form, state, edited);
        }
    }

    private void renderNoteTypes(EventForm form, FieldState state) {
        for (String choice : state.definition.choices()) {
            JsonArray values = state.value.getAsJsonArray();
            boolean selected = values.asList().stream().anyMatch(item -> item.getAsString().equals(choice));
            ImBoolean enabled = new ImBoolean(selected);
            if (ImGui.checkbox(labels.choice(choice) + "##" + choice, enabled)) {
                JsonArray edited = new JsonArray();
                for (JsonElement item : values) if (!item.getAsString().equals(choice)) edited.add(item.deepCopy());
                if (enabled.get()) edited.add(choice);
                commitValue(form, state, edited);
            }
        }
        if (ImGui.button("清空类型（显示全部）")) commitValue(form, state, new JsonArray());
    }

    private void commitInput(EventForm form, FieldState state) {
        EffectParameterResult result = codec.parse(state.definition, state.input.get());
        changed = true;
        state.error = result.error();
        if (result.successful()) {
            codec.write(form.event, state.definition.name(), result.value());
            state.value = result.value().deepCopy();
            state.committed = result.value().deepCopy();
            state.repair.set(result.value().toString());
        }
    }

    private void commitJson(EventForm form, FieldState state, String json) {
        changed = true;
        try {
            commitValue(form, state, JsonParser.parseString(json));
        } catch (com.google.gson.JsonParseException exception) {
            state.error = "JSON 格式错误：" + exception.getMessage();
        }
    }

    private void commitValue(EventForm form, FieldState state, JsonElement value) {
        changed = true;
        state.error = codec.validate(state.definition, value);
        state.value = value.deepCopy();
        if (state.error.isEmpty()) {
            codec.write(form.event, state.definition.name(), value);
            state.committed = value.deepCopy();
            state.input.set(codec.input(state.definition, value));
            state.repair.set(value.toString());
        }
    }

    private void renderRawProperties(EventForm form) {
        if (!ImGui.collapsingHeader("完整参数 JSON（properties，保留扩展字段）")) return;
        if (form.rawProperties == null) form.rawProperties = new ImString(form.event.has("properties") ? form.event.get("properties").toString() : "{}", 4096);
        if (!form.rawPending) form.rawProperties.set(form.event.has("properties") ? form.event.get("properties").toString() : "{}");
        if (ImGui.inputTextMultiline("##properties-json", form.rawProperties, -1, 160, ImGuiInputTextFlags.CallbackResize)) form.rawPending = true;
        if (form.rawPending) ImGui.textWrapped("JSON 草稿尚未应用；应用会替换完整参数，重新读取可放弃此草稿。未处理前不能保存。");
        if (ImGui.button("应用完整 JSON")) {
            try {
                JsonElement properties = JsonParser.parseString(form.rawProperties.get());
                JsonObject candidate = form.event.deepCopy();
                candidate.add("properties", properties);
                List<String> errors = codec.validateEvent(candidate);
                if (errors.isEmpty()) {
                    form.event.add("properties", properties.deepCopy());
                    form.fields.clear();
                    form.rawError = "";
                    form.rawPending = false;
                    changed = true;
                } else form.rawError = String.join("；", errors);
            } catch (com.google.gson.JsonParseException exception) {
                form.rawError = "JSON 格式错误：" + exception.getMessage();
            }
        }
        if (!form.rawError.isEmpty()) ImGui.textColored(0xFF7777FF, form.rawError);
        if (ImGui.button("重新读取已应用参数")) {
            form.rawProperties.set(form.event.has("properties") ? form.event.get("properties").toString() : "{}");
            form.rawError = "";
            form.rawPending = false;
        }
    }

    private static final class EventForm {
        private final JsonObject event;
        private final List<FieldState> fields = new ArrayList<>();
        private ImString rawProperties;
        private String rawError = "";
        private boolean rawPending;

        private EventForm(JsonObject event) {
            this.event = event;
        }
    }

    private static final class FieldState {
        private final EffectParameterField definition;
        private final ImString input = new ImString(2048);
        private final ImString repair = new ImString(2048);
        private final List<ImString> rowTexts = new ArrayList<>();
        private JsonElement value;
        private JsonElement committed;
        private String error = "";

        private FieldState(EffectParameterField definition) {
            this.definition = definition;
        }

        private void load(JsonElement actual, EffectParameterCodec codec) {
            value = actual == null ? null : actual.deepCopy();
            committed = value == null ? null : value.deepCopy();
            input.set(codec.input(definition, value));
            repair.set(value == null ? "" : value.toString());
            error = value == null ? "" : codec.validate(definition, value);
            rowTexts.clear();
        }

        private ImString rowText(int index, String value) {
            while (rowTexts.size() <= index) rowTexts.add(new ImString(1024));
            ImString text = rowTexts.get(index);
            if (!text.get().equals(value)) text.set(value);
            return text;
        }
    }
}
