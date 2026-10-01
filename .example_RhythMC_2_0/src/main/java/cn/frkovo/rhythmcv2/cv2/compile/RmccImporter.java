package cn.frkovo.rhythmcv2.cv2.compile;

import cn.frkovo.rhythmcv2.cv2.chart.Channel;
import cn.frkovo.rhythmcv2.cv2.chart.EditorChart;
import cn.frkovo.rhythmcv2.cv2.chart.EditorLevel;
import cn.frkovo.rhythmcv2.cv2.chart.EditorNote;
import cn.frkovo.rhythmcv2.cv2.chart.EditorNumEvent;
import cn.frkovo.rhythmcv2.cv2.chart.EditorTrack;
import cn.frkovo.rhythmcv2.cv2.core.BeatClock;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 外部谱面导入：manifest.yml（Reborn 字段名）+ world/nether/end/void.rmcc → 精确草稿模型。
 * JSON number 以原始十进制字面量读入 BigDecimal（禁止 new BigDecimal(double) 的 double 路径），
 * 并记 PRECISION_LOSS_RISK（无法恢复 JSON number 之前的真实分数，§7.2）。
 */
public final class RmccImporter {

    private RmccImporter() {
    }

    public static EditorChart importSong(String songFolder, Path dir) throws IOException {
        Path manifestFile = dir.resolve("manifest.yml");
        if (!Files.isRegularFile(manifestFile)) {
            throw new IOException("缺少 manifest.yml");
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(Files.readString(manifestFile, StandardCharsets.UTF_8));
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IOException("manifest.yml 解析失败: " + e.getMessage(), e);
        }

        EditorChart chart = new EditorChart(UUID.randomUUID().toString(), songFolder);
        chart.manifestName = yaml.getString("name", "Unknown Title");
        chart.composer = yaml.getString("composer", "Unknown Composer");
        chart.icon = yaml.getString("icon", "NOTE_BLOCK");
        chart.alias = yaml.getString("alias", "");
        chart.length = String.valueOf(yaml.getInt("length", 0));
        chart.respackSha1 = yaml.getString("respack_sha1", "");
        chart.description = yaml.getString("description", "");
        chart.songId = String.valueOf(yaml.getInt("song_id", 0));
        chart.version = yaml.getString("version", "1.0");
        chart.defaultLocale = yaml.getString("default_locale", "zh-cn");
        chart.comments = new java.util.ArrayList<>(yaml.getStringList("comments"));
        chart.albums = new java.util.ArrayList<>(yaml.getStringList("albums"));
        chart.charters = new java.util.ArrayList<>();
        if (yaml.isConfigurationSection("i18n")) {
            org.bukkit.configuration.ConfigurationSection sec = yaml.getConfigurationSection("i18n");
            for (String locale : sec.getKeys(false)) {
                Object v = sec.get(locale);
                if (v instanceof Map<?, ?> map) {
                    chart.i18n.add(locale, mapToTree(map));
                } else {
                    chart.i18n.addProperty(locale, String.valueOf(v));
                }
            }
        }

        boolean any = false;
        for (String levelName : List.of("world", "nether", "end", "void")) {
            Path f = dir.resolve(levelName + ".rmcc");
            if (!Files.isRegularFile(f)) {
                continue;
            }
            any = true;
            JsonObject root = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            importLevel(chart, levelName, root);
        }
        if (!any) {
            throw new IOException("谱面文件夹没有任何 .rmcc 难度文件");
        }
        if (chart.levels.get("world") != null) {
            chart.activeLevel = "world";
        } else {
            chart.activeLevel = chart.levels.keySet().iterator().next();
        }
        return chart;
    }

    private static void importLevel(EditorChart chart, String levelName, JsonObject root) {
        if (!root.has("meta")) {
            throw new IllegalArgumentException(levelName + ".rmcc 缺少 meta");
        }
        JsonObject meta = root.getAsJsonObject("meta");
        // 首个难度提供 offset/bpms 基线（设计：全曲共享时间轴；后续难度覆盖首项一致性由 Lint 校验）
        if (chart.levels.isEmpty() || chart.bpms.size() == 1) {
            if (meta.has("offset")) {
                chart.offsetMs = String.valueOf(meta.get("offset").getAsLong());
            }
            if (meta.has("bpms") && meta.getAsJsonArray("bpms").size() > 0) {
                chart.bpms.clear();
                for (JsonElement el : meta.getAsJsonArray("bpms")) {
                    JsonObject b = el.getAsJsonObject();
                    chart.bpms.add(new BeatClock.BpmEvent(
                            BeatFraction.fromDecimal(b.get("beat").getAsBigDecimal()),
                            b.get("bpm").getAsBigDecimal()));
                }
                chart.bpms.sort((a, b) -> a.beat().compareTo(b.beat()));
            }
        }
        if (chart.bpms.isEmpty()) {
            chart.bpms.add(new BeatClock.BpmEvent(BeatFraction.ZERO, new BigDecimal("120")));
        }

        EditorLevel level = new EditorLevel(levelName);
        level.levelValue = meta.has("level") ? meta.get("level").getAsString() : "1";
        level.initialArena = meta.has("initialArena") ? meta.get("initialArena").getAsString() : "";
        chart.levels.put(levelName, level);

        if (root.has("tracks")) {
            for (JsonElement tel : root.getAsJsonArray("tracks")) {
                JsonObject t = tel.getAsJsonObject();
                EditorTrack track = new EditorTrack("track-" + t.get("id").getAsInt());
                for (Channel c : Channel.values()) {
                    if (!t.has(c.json)) {
                        continue;
                    }
                    List<EditorNumEvent> list = track.events.get(c);
                    for (JsonElement eel : t.getAsJsonArray(c.json)) {
                        JsonObject ev = eel.getAsJsonObject();
                        list.add(new EditorNumEvent(chart.nextEventId(),
                                BeatFraction.fromDecimal(ev.get("startBeat").getAsBigDecimal()),
                                BeatFraction.fromDecimal(ev.get("endBeat").getAsBigDecimal()),
                                ev.get("startValue").getAsBigDecimal(),
                                ev.get("endValue").getAsBigDecimal(),
                                ev.has("easingType") ? ev.get("easingType").getAsInt() : 0));
                    }
                }
                if (t.has("notes")) {
                    for (JsonElement nel : t.getAsJsonArray("notes")) {
                        JsonObject n = nel.getAsJsonObject();
                        EditorNote note = new EditorNote(chart.nextNoteId(),
                                n.get("noteType").getAsInt(),
                                BeatFraction.fromDecimal(n.get("beat").getAsBigDecimal()));
                        BigDecimal[] pos = triple(n.getAsJsonArray("pos"));
                        BigDecimal[] scale = triple(n.getAsJsonArray("scale"));
                        BigDecimal[] rot = triple(n.getAsJsonArray("rotation"));
                        note.pos = pos;
                        note.scale = scale;
                        note.rotation = rot;
                        note.holdGroup = n.has("holdGroup") ? n.get("holdGroup").getAsInt() : -1;
                        track.notes.put(note.id, note);
                    }
                }
                level.tracks.put(track.id, track);
            }
        }
        if (root.has("effects")) {
            for (JsonElement fx : root.getAsJsonArray("effects")) {
                level.effects.add(fx.getAsJsonObject().deepCopy());
            }
        }
        if (meta.has("charters") && meta.get("charters").isJsonArray()) {
            for (JsonElement c : meta.getAsJsonArray("charters")) {
                if (!chart.charters.contains(c.getAsString())) {
                    chart.charters.add(c.getAsString());
                }
            }
        }
    }

    private static BigDecimal[] triple(JsonArray a) {
        BigDecimal[] out = new BigDecimal[3];
        for (int i = 0; i < 3; i++) {
            out[i] = a.get(i).getAsBigDecimal();
        }
        return out;
    }

    /** Map（Bukkit i18n section）→ Gson 树。 */
    static JsonObject mapToTree(Map<?, ?> map) {
        JsonObject o = new JsonObject();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof Map<?, ?> sub) {
                o.add(String.valueOf(e.getKey()), mapToTree(sub));
            } else if (v instanceof Number num) {
                o.addProperty(String.valueOf(e.getKey()), num);
            } else if (v instanceof Boolean b) {
                o.addProperty(String.valueOf(e.getKey()), b);
            } else {
                o.addProperty(String.valueOf(e.getKey()), String.valueOf(v));
            }
        }
        return o;
    }
}
