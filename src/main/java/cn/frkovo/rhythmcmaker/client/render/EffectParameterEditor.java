package cn.frkovo.rhythmcmaker.client.render;

import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterCodec;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterControl;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterField;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterResult;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterSchema;
import cn.frkovo.rhythmcmaker.common.effect.parameter.EffectParameterType;
import cn.frkovo.rhythmcmaker.common.text.EffectTextFormatter;
import cn.frkovo.rhythmcmaker.chart.ChartManifest;
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
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;

public final class EffectParameterEditor {
    private final EffectParameterCodec codec = EffectParameterCodec.getInstance();
    private final EffectParameterLabels labels = EffectParameterLabels.getInstance();
    private final List<EventForm> forms = new ArrayList<>();
    private boolean changed;
    private List<JsonObject> availableEvents = List.of();
    private List<String> availableSceneNames = List.of();
    private List<Integer> availableTrackIds = List.of();

    public boolean render(JsonObject event, List<JsonObject> events, ChartManifest chart) {
        changed = false;
        availableEvents = events == null ? List.of(event) : List.copyOf(events);
        availableSceneNames = sceneNames(chart);
        availableTrackIds = trackIds(chart);
        EffectParameterType type = EffectParameterSchema.getInstance().find(codec.type(event));
        if (type == null) return false;
        EventForm form = form(event);
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
        if (isTextDisplayGlowingField(form, field) && actual == null) {
            codec.write(form.event, field.name(), new JsonPrimitive(false));
            state.load(codec.read(form.event, field.name()), codec);
            actual = codec.read(form.event, field.name());
            changed = true;
        }
        ImGui.textWrapped(field.label());
        if (!field.help().isBlank()) ImGui.textWrapped(field.help());
        if (isWeatherField(form, field)) { renderWeather(form, state); return; }
        if (isArenaField(form, field)) { renderArena(form, state); return; }
        if (isNoteTypesField(form, field)) { renderNoteTypesMenu(form, state); return; }
        if (isTextDisplayTrackField(form, field)) { renderTextDisplayTrack(form, state); return; }
        if (isTracksField(form, field)) { renderTracksMenu(form, state); return; }
        if (isClearEffectsField(form, field)) {
            renderClearEffects(form, state, actual == null);
            return;
        }
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
                else ImGui.textDisabled("未设置：沿用 Reborn 官方的省略语义。");
                return;
            }
            if (actual.isJsonArray()) {
                ImGui.textDisabled(actual.getAsJsonArray().isEmpty()
                        ? "显式空数组：不处理任何目标，不等同于省略该参数。"
                        : "显式数组值：仅处理列出的目标。" );
            }
        } else if (actual == null) {
            ImGui.textColored(0xFF7777FF, "缺少必填参数，请填写后保存。");
            if (!isRequiredHologramId(form, field)) return;
            state.value = new JsonPrimitive("");
            state.input.set("");
        }
        ImGui.setNextItemWidth(-1);
        if (!shapeMatches(field.control(), state.value)) {
            if (ImGui.inputTextMultiline("##repair-value", state.repair, -1, 70, ImGuiInputTextFlags.CallbackResize)) {
                commitJson(form, state, state.repair.get());
            }
        } else {
            switch (field.control()) {
                case TEXT, LONG, DECIMAL, INTEGER_LIST -> {
                    if (ImGui.inputText("##value", state.input, ImGuiInputTextFlags.CallbackResize)) commitInput(form, state);
                }
                case INTEGER -> {
                    if (field.name().equals("effectId")) renderPotion(form, state);
                    else if (ImGui.inputText("##value", state.input, ImGuiInputTextFlags.CallbackResize)) commitInput(form, state);
                }
                case MULTILINE_TEXT -> {
                    if (ImGui.inputTextMultiline("##value", state.input, -1, 80, ImGuiInputTextFlags.CallbackResize)) commitInput(form, state);
                }
                case ENUM -> renderEnum(form, state);
                case BOOLEAN -> {
                    ImBoolean value = new ImBoolean(state.value.getAsBoolean());
                    String checkboxLabel = field.name().equals("glowing") ? "发光##value" : "开启##value";
                    if (ImGui.checkbox(checkboxLabel, value)) commitValue(form, state, new JsonPrimitive(value.get()));
                }
                case VECTOR, RGBA -> {
                    if (isTextDisplayRotation(form, field)) renderAngleVector(form, state);
                    else renderVector(form, state, state.value.getAsJsonArray(), -1);
                }
                case VECTOR_LIST, RGB_LIST -> renderVectorList(form, state);
                case STRING_LIST -> renderStringList(form, state);
                case NOTE_TYPES -> renderNoteTypes(form, state);
            }
        }
        renderTextPreview(form, field, state);
        if (isHologramId(form, field) || isTextDisplayId(form, field)) {
            ImGui.sameLine();
            if (ImGui.smallButton("随机生成 ID")) {
                commitValue(form, state, new JsonPrimitive(isTextDisplayId(form, field)
                        ? generateTextDisplayId() : generateHologramId()));
            }
        }
        if (!state.error.isEmpty()) ImGui.textColored(0xFF7777FF, state.error);
        if (shouldShowDefaultReset(form, field) && ImGui.smallButton("恢复该参数默认值")) {
            commitValue(form, state, JsonParser.parseString(field.defaultJson()));
        }
    }

    private boolean isRequiredHologramId(EventForm form, EffectParameterField field) {
        String type = codec.type(form.event);
        return (type.equals("HOLOGRAM") || type.equals("REMOVE_HOLOGRAM")) && field.name().equals("id");
    }

    private boolean isHologramId(EventForm form, EffectParameterField field) {
        return codec.type(form.event).equals("HOLOGRAM") && field.name().equals("id");
    }

    private boolean isTextDisplayId(EventForm form, EffectParameterField field) {
        return codec.type(form.event).equals("TEXT_DISPLAY") && field.name().equals("id");
    }

    private boolean isTextDisplayRotation(EventForm form, EffectParameterField field) {
        return codec.type(form.event).equals("TEXT_DISPLAY") && field.name().equals("rotation");
    }

    private boolean isTextDisplayTrackField(EventForm form, EffectParameterField field) {
        return codec.type(form.event).equals("TEXT_DISPLAY_SYNC_TRACK") && field.name().equals("track");
    }

    private boolean isTextDisplayResetField(EventForm form, EffectParameterField field) {
        String type = codec.type(form.event);
        if (type.equals("TEXT_DISPLAY") && (field.name().equals("text") || field.name().equals("id"))) return true;
        if (type.equals("TEXT_DISPLAY_EFFECT") && (field.name().equals("id") || field.name().equals("type")
                || field.name().equals("text") || field.name().equals("shadowed") || field.name().equals("glowing"))) return true;
        if (type.equals("TEXT_DISPLAY_REMOVE") && field.name().equals("id")) return true;
        if (type.equals("TEXT_DISPLAY_SYNC_TRACK") && (field.name().equals("id") || field.name().equals("track"))) return true;
        return type.equals("TEXT_DISPLAY_DESYNC_TRACK") && field.name().equals("id");
    }

    private boolean isTextDisplayGlowingField(EventForm form, EffectParameterField field) {
        return codec.type(form.event).equals("TEXT_DISPLAY_EFFECT") && field.name().equals("glowing")
                && "GLOWING".equals(codec.read(form.event, "type").getAsString());
    }

    private boolean isHologramContents(EventForm form, FieldState state) {
        return codec.type(form.event).equals("HOLOGRAM") && state.definition.name().equals("contents");
    }

    private boolean shouldShowDefaultReset(EventForm form, EffectParameterField field) {
        String type = codec.type(form.event);
        if (isRequiredHologramId(form, field)) return false;
        if (isTextDisplayResetField(form, field)) return false;
        if (type.equals("HOLOGRAM") && field.name().equals("contents")) return false;
        if (type.equals("TITLE") && (field.name().equals("title") || field.name().equals("subtitle"))) return false;
        if (type.equals("FIREWORK") && field.name().equals("type")) return false;
        if (type.equals("EFFECT") && field.name().equals("effectId")) return false;
        if (type.equals("CLEAR_EFFECT") && field.name().equals("effects")) return false;
        if (type.equals("WEATHER") && field.name().equals("weather")) return false;
        if (type.equals("ARENA") && field.name().equals("arena")) return false;
        if (type.equals("HIDE_NOTES") && (field.name().equals("noteTypes") || field.name().equals("tracks"))) return false;
        if (type.equals("GLOW_COLOR") && field.name().equals("color")) return false;
        return !isVectorListWithItemReset(field);
    }

    private String generateHologramId() {
        Set<String> used = new HashSet<>();
        for (JsonObject event : availableEvents) {
            JsonElement id = codec.read(event, "id");
            if (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()) used.add(id.getAsString());
        }
        for (int attempt = 0; attempt < 100000; attempt++) {
            String candidate = String.format(Locale.ROOT, "H%05d", ThreadLocalRandom.current().nextInt(100000));
            if (!used.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("无法生成未重复的全息文字 ID");
    }

    private String generateTextDisplayId() {
        Set<String> used = new HashSet<>();
        for (JsonObject event : availableEvents) {
            if (!codec.type(event).startsWith("TEXT_DISPLAY")) continue;
            JsonElement id = codec.read(event, "id");
            if (id != null && id.isJsonPrimitive() && id.getAsJsonPrimitive().isString()) used.add(id.getAsString());
        }
        for (int attempt = 0; attempt < 100000; attempt++) {
            String candidate = String.format(Locale.ROOT, "T%05d", ThreadLocalRandom.current().nextInt(100000));
            if (!used.contains(candidate)) return candidate;
        }
        throw new IllegalStateException("无法生成未重复的文本展示实体 ID");
    }

    private boolean isVectorListWithItemReset(EffectParameterField field) {
        return field.control() == EffectParameterControl.VECTOR_LIST || field.control() == EffectParameterControl.RGB_LIST;
    }

    private String vectorListItemDefault(EffectParameterField field) {
        return field.control() == EffectParameterControl.RGB_LIST ? "[255,255,255]" : "[0,2,0]";
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

    private boolean isWeatherField(EventForm form, EffectParameterField field) {
        return codec.type(form.event).equals("WEATHER") && field.name().equals("weather");
    }

    private boolean isArenaField(EventForm form, EffectParameterField field) {
        return codec.type(form.event).equals("ARENA") && field.name().equals("arena");
    }

    private boolean isNoteTypesField(EventForm form, EffectParameterField field) {
        return codec.type(form.event).equals("HIDE_NOTES") && field.name().equals("noteTypes");
    }

    private boolean isTracksField(EventForm form, EffectParameterField field) {
        return codec.type(form.event).equals("HIDE_NOTES") && field.name().equals("tracks");
    }

    private List<String> sceneNames(ChartManifest chart) {
        if (chart == null) return List.of();
        Set<String> names = new java.util.TreeSet<>();
        if (chart.initialArena != null && !chart.initialArena.isBlank()) names.add(chart.initialArena);
        if (chart.arenaBindings != null) for (String name : chart.arenaBindings.keySet()) if (name != null && !name.isBlank()) names.add(name);
        return List.copyOf(names);
    }

    private List<Integer> trackIds(ChartManifest chart) {
        if (chart == null) return List.of();
        Set<Integer> ids = new java.util.TreeSet<>();
        if (chart.tracks != null) for (ChartManifest.Track track : chart.tracks) if (track != null) ids.add(track.id);
        if (chart.notes != null) for (ChartManifest.Note note : chart.notes) if (note != null) ids.add(note.trackId);
        return List.copyOf(ids);
    }

    private void renderWeather(EventForm form, FieldState state) {
        String value = state.value == null ? "CLEAR" : state.value.getAsString();
        if (ImGui.beginCombo("##weather", weatherName(value))) {
            for (String weather : List.of("CLEAR", "RAIN", "THUNDER")) if (ImGui.selectable(weatherName(weather) + "##weather-" + weather, weather.equals(value))) commitValue(form, state, new JsonPrimitive(weather));
            ImGui.endCombo();
        }
    }

    private String weatherName(String weather) {
        return switch (weather) {
            case "CLEAR" -> "晴天";
            case "RAIN" -> "雨天";
            case "THUNDER" -> "雷暴";
            default -> weather;
        };
    }

    private void renderArena(EventForm form, FieldState state) {
        String value = state.value == null ? "" : state.value.getAsString();
        if (availableSceneNames.isEmpty()) {
            ImGui.textColored(0xFF7777FF, "当前谱面没有可用场景。");
            return;
        }
        if (ImGui.beginCombo("##arena", value)) {
            for (String name : availableSceneNames) if (ImGui.selectable(name + "##arena-" + name, name.equals(value))) commitValue(form, state, new JsonPrimitive(name));
            ImGui.endCombo();
        }
        if (!availableSceneNames.contains(value)) ImGui.textColored(0xFF7777FF, "当前场景不存在，请重新选择。");
    }

    private void renderNoteTypesMenu(EventForm form, FieldState state) {
        JsonElement value = state.value;
        boolean restore = value == null || !value.isJsonArray() || value.getAsJsonArray().isEmpty();
        String preview = restore ? "恢复显示" : selectedNoteTypesPreview(value.getAsJsonArray());
        if (ImGui.beginCombo("##note-types", preview)) {
            if (ImGui.selectable("恢复显示##note-types-restore", restore)) commitValue(form, state, new JsonArray());
            ImGui.separator();
            for (String type : state.definition.choices()) {
                boolean selected = containsString(value, type);
                ImBoolean enabled = new ImBoolean(selected);
                if (ImGui.checkbox(labels.choice(type) + "##note-type-" + type, enabled)) {
                    JsonArray values = value != null && value.isJsonArray() ? value.getAsJsonArray().deepCopy() : new JsonArray();
                    if (enabled.get()) values.add(type); else removeString(values, type);
                    commitValue(form, state, values);
                }
            }
            ImGui.endCombo();
        }
    }

    private String selectedNoteTypesPreview(JsonArray values) {
        List<String> names = new ArrayList<>();
        for (JsonElement value : values) if (value.isJsonPrimitive()) names.add(labels.choice(value.getAsString()));
        return names.isEmpty() ? "恢复显示" : String.join("、", names);
    }

    private boolean containsString(JsonElement values, String expected) {
        if (values == null || !values.isJsonArray()) return false;
        for (JsonElement value : values.getAsJsonArray()) if (value.isJsonPrimitive() && expected.equals(value.getAsString())) return true;
        return false;
    }

    private void removeString(JsonArray values, String expected) {
        for (int index = values.size() - 1; index >= 0; index--) if (expected.equals(values.get(index).getAsString())) values.remove(index);
    }

    private void renderTracksMenu(EventForm form, FieldState state) {
        JsonElement value = state.value;
        boolean restore = value == null || !value.isJsonArray() || value.getAsJsonArray().isEmpty();
        boolean all = !restore && selectedAllTracks(value.getAsJsonArray());
        String preview = restore ? "恢复显示" : all ? "全部轨道" : selectedTracksPreview(value.getAsJsonArray());
        if (ImGui.beginCombo("##tracks", preview)) {
            if (ImGui.selectable("恢复显示##tracks-restore", restore)) commitValue(form, state, new JsonArray());
            ImGui.separator();
            if (ImGui.selectable("全部轨道##tracks-all", all)) commitValue(form, state, all ? new JsonArray() : allTrackArray());
            ImGui.separator();
            for (Integer trackId : availableTrackIds) {
                boolean selected = containsInteger(value, trackId);
                ImBoolean enabled = new ImBoolean(selected);
                if (all) ImGui.beginDisabled();
                if (ImGui.checkbox("轨道 " + trackId + "##track-" + trackId, enabled)) {
                    JsonArray values = value != null && value.isJsonArray() ? value.getAsJsonArray().deepCopy() : new JsonArray();
                    if (all) values = new JsonArray();
                    if (enabled.get()) values.add(trackId); else removeInteger(values, trackId);
                    commitValue(form, state, values);
                }
                if (all) ImGui.endDisabled();
            }
            ImGui.endCombo();
        }
    }

    private JsonArray allTrackArray() {
        JsonArray values = new JsonArray();
        for (Integer trackId : availableTrackIds) values.add(trackId);
        return values;
    }

    private boolean selectedAllTracks(JsonArray values) {
        return !availableTrackIds.isEmpty() && values.size() == availableTrackIds.size() && availableTrackIds.stream().allMatch(id -> containsInteger(values, id));
    }

    private String selectedTracksPreview(JsonArray values) {
        List<String> names = new ArrayList<>();
        for (JsonElement value : values) if (value.isJsonPrimitive()) names.add("轨道 " + value.getAsInt());
        return names.isEmpty() ? "恢复显示" : String.join("、", names);
    }

    private boolean containsInteger(JsonElement values, int expected) {
        if (values == null || !values.isJsonArray()) return false;
        for (JsonElement value : values.getAsJsonArray()) if (value.isJsonPrimitive() && value.getAsInt() == expected) return true;
        return false;
    }

    private void removeInteger(JsonArray values, int expected) {
        for (int index = values.size() - 1; index >= 0; index--) if (values.get(index).isJsonPrimitive() && values.get(index).getAsInt() == expected) values.remove(index);
    }

    private boolean isClearEffectsField(EventForm form, EffectParameterField field) {
        return codec.type(form.event).equals("CLEAR_EFFECT") && field.name().equals("effects");
    }

    private void renderPotion(EventForm form, FieldState state) {
        int selected = state.value.getAsInt();
        if (ImGui.beginCombo("##value", labels.potionName(selected))) {
            for (int id = 1; id <= 33; id++) {
                if (ImGui.selectable(labels.potionName(id) + "##potion-" + id, id == selected)) {
                    commitValue(form, state, new JsonPrimitive(id));
                }
            }
            ImGui.endCombo();
        }
    }

    private void renderTextDisplayTrack(EventForm form, FieldState state) {
        if (availableTrackIds.isEmpty()) {
            ImGui.textDisabled("暂无可用轨道");
            return;
        }
        int selected = state.value == null ? availableTrackIds.get(0) : state.value.getAsInt();
        String preview = availableTrackIds.contains(selected) ? "轨道 " + selected : "轨道不存在：" + selected;
        if (ImGui.beginCombo("##text-display-track", preview)) {
            for (Integer trackId : availableTrackIds) {
                if (ImGui.selectable("轨道 " + trackId + "##text-display-track-" + trackId, trackId == selected)) {
                    commitValue(form, state, new JsonPrimitive(trackId));
                }
            }
            ImGui.endCombo();
        }
        if (!availableTrackIds.contains(selected)) ImGui.textColored(0xFF7777FF, "当前轨道不存在，请重新选择。");
    }

    private void renderClearEffects(EventForm form, FieldState state, boolean allEffects) {
        if (state.value != null && state.value.isJsonArray() && state.value.getAsJsonArray().isEmpty()) {
            state.error = "至少选择一个药水效果，或选择全部药水效果；若不清除药水效果，请删除此特效。";
        }
        String preview = allEffects ? "全部药水效果" : clearEffectsPreview(state.value);
        if (ImGui.beginCombo("##clear-effects", preview)) {
            if (ImGui.selectable("全部药水效果##clear-all", allEffects) && !allEffects) {
                codec.remove(form.event, "effects");
                state.load(null, codec);
                changed = true;
                allEffects = true;
            }
            ImGui.separator();
            for (int id = 1; id <= 33; id++) {
                boolean selected = containsPotion(state.value, id);
                ImBoolean enabled = new ImBoolean(selected);
                if (ImGui.checkbox(labels.potionName(id) + "##clear-" + id, enabled)) {
                    JsonArray values = state.value == null ? new JsonArray() : state.value.getAsJsonArray().deepCopy();
                    if (enabled.get()) values.add(id);
                    else {
                        for (int index = values.size() - 1; index >= 0; index--) {
                            if (values.get(index).getAsInt() == id) values.remove(index);
                        }
                    }
                    commitValue(form, state, values);
                    if (values.isEmpty()) state.error = "至少选择一个药水效果，或选择全部药水效果。";
                    allEffects = false;
                }
            }
            ImGui.endCombo();
        }
        if (!state.error.isEmpty()) ImGui.textColored(0xFF7777FF, state.error);
    }

    private boolean containsPotion(JsonElement selectedEffects, int potionId) {
        if (selectedEffects == null || !selectedEffects.isJsonArray()) return false;
        for (JsonElement selectedEffect : selectedEffects.getAsJsonArray()) {
            if (selectedEffect.isJsonPrimitive() && selectedEffect.getAsJsonPrimitive().isNumber()
                    && selectedEffect.getAsInt() == potionId) return true;
        }
        return false;
    }

    private String clearEffectsPreview(JsonElement selectedEffects) {
        if (selectedEffects == null || !selectedEffects.isJsonArray() || selectedEffects.getAsJsonArray().isEmpty()) return "请选择药水效果";
        List<String> names = new ArrayList<>();
        for (JsonElement selectedEffect : selectedEffects.getAsJsonArray()) {
            if (selectedEffect.isJsonPrimitive() && selectedEffect.getAsJsonPrimitive().isNumber()) {
                names.add(labels.potionName(selectedEffect.getAsInt()));
            }
        }
        return names.isEmpty() ? "请选择药水效果" : String.join("、", names);
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

    private void renderAngleVector(EventForm form, FieldState state) {
        JsonArray row = state.value.getAsJsonArray();
        JsonArray edited = row.deepCopy();
        float width = Math.max(32, (ImGui.getContentRegionAvailX() - 12) / row.size());
        boolean editedValue = false;
        for (int component = 0; component < row.size(); component++) {
            if (component > 0) ImGui.sameLine();
            ImGui.beginGroup();
            ImGui.pushTextWrapPos(ImGui.getCursorPosX() + width);
            ImGui.textWrapped(labels.component(state.definition, component));
            ImGui.popTextWrapPos();
            ImGui.setNextItemWidth(width);
            ImString angle = state.angleText(component, formatAngle(Math.toDegrees(row.get(component).getAsDouble())));
            if (ImGui.inputText("##angle-" + component, angle, ImGuiInputTextFlags.CallbackResize)) {
                try {
                    double degrees = Double.parseDouble(angle.get().trim());
                    if (Double.isFinite(degrees)) {
                        edited.set(component, new JsonPrimitive(Math.toRadians(degrees)));
                        editedValue = true;
                        state.error = "";
                    }
                } catch (NumberFormatException exception) {
                    state.error = "请输入有效角度。";
                }
            }
            ImGui.endGroup();
        }
        if (editedValue) commitValue(form, state, edited);
    }

    private String formatAngle(double degrees) {
        String value = String.format(Locale.ROOT, "%.6f", degrees);
        while (value.contains(".") && value.endsWith("0")) value = value.substring(0, value.length() - 1);
        if (value.endsWith(".")) value = value.substring(0, value.length() - 1);
        return value;
    }

    private void renderVectorList(EventForm form, FieldState state) {
        JsonArray values = state.value.getAsJsonArray();
        int removeIndex = -1;
        int resetIndex = -1;
        for (int index = 0; index < values.size(); index++) {
            ImGui.pushID(index);
            ImGui.text("第 " + (index + 1) + " 项");
            renderVector(form, state, values.get(index).getAsJsonArray(), index);
            if (ImGui.smallButton("删除此项")) removeIndex = index;
            if (isVectorListWithItemReset(state.definition) && ImGui.smallButton("恢复此项默认值")) resetIndex = index;
            ImGui.popID();
        }
        if (resetIndex >= 0 && resetIndex < state.value.getAsJsonArray().size()) {
            JsonArray edited = state.value.getAsJsonArray().deepCopy();
            edited.set(resetIndex, JsonParser.parseString(vectorListItemDefault(state.definition)));
            commitValue(form, state, edited);
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
        if (isMessageContents(form, state)) {
            if (ImGui.inputTextMultiline("##message-contents", state.input, -1, 90, ImGuiInputTextFlags.CallbackResize)) commitInput(form, state);
            return;
        }
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
            if (!isHologramContents(form, state) && ImGui.smallButton("删除此条")) removeIndex = index;
            ImGui.popID();
        }
        if (removeIndex >= 0) {
            JsonArray edited = state.value.getAsJsonArray().deepCopy();
            edited.remove(removeIndex);
            state.rowTexts.clear();
            commitValue(form, state, edited);
        }
        if (!isHologramContents(form, state) && ImGui.button("新增文字条目")) {
            JsonArray edited = state.value.getAsJsonArray().deepCopy();
            edited.add("");
            commitValue(form, state, edited);
        }
    }

    private boolean isMessageContents(EventForm form, FieldState state) {
        return codec.type(form.event).equals("MESSAGE") && state.definition.name().equals("contents");
    }

    private void renderTextPreview(EventForm form, EffectParameterField field, FieldState state) {
        if (!isPreviewField(form, field) || !state.error.isEmpty()) return;
        List<String> values = new ArrayList<>();
        if (field.control() == EffectParameterControl.STRING_LIST && state.value.isJsonArray()) {
            for (JsonElement value : state.value.getAsJsonArray()) values.add(value.getAsString());
        } else if (state.value.isJsonPrimitive() && state.value.getAsJsonPrimitive().isString()) values.add(state.value.getAsString());
        if (values.isEmpty()) return;
        ImGui.textDisabled("预览");
        for (String value : values) {
            for (EffectTextFormatter.PreviewSegment segment : EffectTextFormatter.getInstance().preview(value, 0xFFFFFF)) {
                ImGui.sameLine(0, 0);
                int colour = 0xFF000000 | (segment.color() & 0xFFFFFF);
                String text = segment.obfuscated() ? obfuscate(segment.text()) : segment.text();
                if (segment.bold()) text = "▌" + text;
                if (segment.italic()) text = "╱" + text;
                ImGui.textColored(colour, text);
            }
            ImGui.newLine();
        }
    }

    private boolean isPreviewField(EventForm form, EffectParameterField field) {
        String type = codec.type(form.event);
        return (type.equals("TITLE") && (field.name().equals("title") || field.name().equals("subtitle")))
                || (type.equals("HOLOGRAM") && field.name().equals("contents"))
                || (type.equals("MESSAGE") && field.name().equals("contents"))
                || (type.equals("TEXT_DISPLAY") && field.name().equals("text"))
                || (type.equals("TEXT_DISPLAY_EFFECT") && field.name().equals("text"));
    }

    private String obfuscate(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            result.append(Character.isWhitespace(character) ? character : '█');
        }
        return result.toString();
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
        private final List<ImString> angleTexts = new ArrayList<>();
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
            angleTexts.clear();
        }

        private ImString rowText(int index, String value) {
            while (rowTexts.size() <= index) rowTexts.add(new ImString(1024));
            ImString text = rowTexts.get(index);
            if (!text.get().equals(value)) text.set(value);
            return text;
        }

        private ImString angleText(int index, String value) {
            while (angleTexts.size() <= index) angleTexts.add(new ImString(128));
            ImString text = angleTexts.get(index);
            if (!text.get().equals(value) && !ImGui.isItemActive()) text.set(value);
            return text;
        }
    }
}
