package cn.frkovo.rhythmcmaker.chart;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;

public final class NoteEditingRegression {
    public static void main(String[] args) {
        JsonObject legacy = JsonParser.parseString("""
                {"frames":[{"judge-tick":120,"notes":[
                    {"type":"NOTE_HOLD","length":1,"pos":-1},
                    {"type":"NOTE_HOLD","length":5,"pos":1}
                ]}]}
                """).getAsJsonObject();
        ChartManifest chart = importLegacy(legacy);
        ChartManifest.Note ordinary = chart.notes.getFirst();
        require(ordinary.holdGroup == -1, "length=1 legacy HOLD imports as an ordinary editable HOLD");
        require(HoldGroupRules.isEndpoint(chart, ordinary), "ordinary imported HOLD can open the note editor");
        ChartManifest.Note head = chart.notes.get(1);
        ChartManifest.Note tail = chart.notes.getLast();
        require(head.holdGroup >= 0 && head.holdGroup == tail.holdGroup, "long legacy HOLD preserves its group");
        require(HoldGroupRules.otherEndpoint(chart, head) == tail, "multi-member imported HOLD resolves its paired endpoint");
        if (args.length > 0) verifyProvidedChart(Path.of(args[0]));
        verifyRhythmc3ExportContract();
        verifyLayoutHeightAndBounds();
        verifyBeatDraftPersistence();
        verifyBeatGridBoundaries();
        verifySaveSnapshotPreservesFraction();
        verifyGeometryAndOriginRoundTrip();
        verifyEditingValidation();
        verifyHoldLifecycleAndNativeRoundTrip();
        verifySnapOrderAndTrackIsolation();
        verifyWindowDefaults();
        System.out.println("Note editing regression passed");
    }

    private static void verifyProvidedChart(Path file) {
        try {
            JsonObject source = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            ChartManifest chart = importLegacy(source);
            require(chart.notes.size() > 3000, "provided chart imports all legacy notes");
            int expectedNotes = 0;
            int expectedGroups = 0;
            for (var frame : source.getAsJsonArray("frames")) for (var element : frame.getAsJsonObject().getAsJsonArray("notes")) {
                JsonObject entry = element.getAsJsonObject();
                boolean hold = entry.get("type").getAsString().equals("NOTE_HOLD");
                int length = hold && entry.has("length") ? Math.max(1, entry.get("length").getAsInt()) : 1;
                expectedNotes += length;
                if (hold && length > 1) expectedGroups++;
            }
            require(chart.notes.size() == expectedNotes, "provided chart does not lose or add any real judgement notes");
            require(HoldInterval.fromChart(chart).size() == expectedGroups, "provided chart preserves every real long Hold group");
            for (ChartManifest.Note note : chart.notes) if (note.type == 2 && HoldGroupRules.isEndpoint(chart, note)) {
                require(cn.frkovo.rhythmcmaker.common.note.NoteEditingModel.getInstance().canEdit(chart, note),
                        "every imported ordinary HOLD and Hold endpoint can be edited without leaving the chart");
            }
            JsonObject exported = Rhythmc3Exporter.trackJson(chart, new ChartManifest.Track(0), chart.totalBeats + 8);
            ChartManifest roundTrip = new ChartManifest(); roundTrip.bpm = chart.bpm;
            ChartStorage.importRhythmc3Track(roundTrip, ChartTiming.prepare(roundTrip), exported);
            require(roundTrip.notes.size() == expectedNotes && HoldInterval.fromChart(roundTrip).size() == expectedGroups,
                    "the complete provided chart survives native export and import with all Hold groups intact");
            System.out.println("Provided chart: " + expectedNotes + " runtime notes, " + expectedGroups + " Hold groups; native round-trip passed");
            require(chart.notes.stream().anyMatch(note -> note.holdGroup >= 0), "provided long Holds import as grouped notes");
            require(chart.notes.stream().filter(note -> note.type == 2 && note.holdGroup < 0).count() > 0,
                    "provided single-beat HOLD notes remain ordinary HOLDs");
            for (ChartManifest.Note note : chart.notes) if (note.holdGroup >= 0) {
                require(HoldGroupRules.isValidGroup(chart, note), "imported Hold group endpoints keep valid beat order");
                boolean endpoint = note.holdRole == cn.frkovo.rhythmcmaker.common.note.HoldRole.HEAD
                        || note.holdRole == cn.frkovo.rhythmcmaker.common.note.HoldRole.TAIL;
                require(HoldGroupRules.isEndpoint(chart, note) == endpoint, "only imported Hold endpoints resolve as editable targets");
            }
        } catch (java.io.IOException exception) {
            throw new AssertionError("Unable to read provided chart", exception);
        }
    }

    private static void verifyRhythmc3ExportContract() {
        ChartManifest chart = new ChartManifest();
        ChartManifest.Track track = new ChartManifest.Track(0);
        chart.tracks.add(track);
        ChartManifest.Note endpoint = new ChartManifest.Note();
        endpoint.id = "endpoint"; endpoint.type = 2; endpoint.holdGroup = 7; endpoint.trackId = 0; endpoint.beat = 1.0;
        ChartManifest.Note visual = new ChartManifest.Note();
        visual.id = "visual"; visual.type = 2; visual.trackId = 0; visual.visualOnly = true;
        chart.notes.add(endpoint); chart.notes.add(visual);
        com.google.gson.JsonObject exported = Rhythmc3Exporter.trackJson(chart, track, 8.0);
        com.google.gson.JsonArray notes = exported.getAsJsonArray("notes");
        require(notes.size() == 1, "export excludes Maker-only visual fill notes");
        com.google.gson.JsonObject note = notes.get(0).getAsJsonObject();
        require(note.get("holdGroup").getAsInt() == 7 && !note.has("holdRole") && !note.has("visualOnly"),
                "export preserves official Hold group ID but excludes Maker-only fields");
        require(note.has("noteType") && note.has("beat") && note.has("pos") && note.has("scale") && note.has("rotation"),
                "export retains official playback note fields");
    }

    private static void verifyLayoutHeightAndBounds() {
        ChartManifest chart = new ChartManifest(); chart.laneCount = 5; chart.trackHeight = 4;
        EditorTrackLayout layout = EditorTrackLayout.of(chart);
        require(layout.halfWidth() == 2 && layout.maximumRelativeY() == 3, "editor ranges use lane and height settings");
        require(EditorTrackLayout.of(3, 4, 5).maximumRelativeY() == 4, "height five exposes relative Y range zero through four");
        require(!layout.hasNotesAbove(chart), "empty chart permits reduced height");
        ChartManifest.Note tall = new ChartManifest.Note(); tall.type = 0; tall.y = 70.0; chart.notes.add(tall);
        require(layout.hasNotesAbove(chart), "height decrease detects notes above new track ceiling");
    }

    private static void verifyBeatDraftPersistence() {
        ChartManifest chart = new ChartManifest();
        ChartManifest.Note note = new ChartManifest.Note(); note.id = "fraction"; note.beat = 2.5;
        note.editorBeat = new cn.frkovo.rhythmcmaker.common.note.EditorBeat(2, 2, 4); chart.notes.add(note);
        String draft = cn.frkovo.rhythmcmaker.common.note.NoteEditorDraft.serialize(chart);
        ChartManifest restored = new ChartManifest();
        ChartManifest.Note restoredNote = new ChartManifest.Note(); restoredNote.id = "fraction"; restoredNote.beat = 2.5;
        restored.notes.add(restoredNote);
        cn.frkovo.rhythmcmaker.common.note.NoteEditorDraft.apply(draft, restored);
        require(restoredNote.editorBeat != null && restoredNote.editorBeat.numerator() == 2
                        && restoredNote.editorBeat.denominator() == 4,
                "Maker draft preserves the user's unreduced fraction spelling");
    }

    private static void verifyBeatGridBoundaries() {
        ChartManifest chart = new ChartManifest();
        chart.divisionsPerBeat = 12;
        for (int wholeBeat = 1; wholeBeat <= 256; wholeBeat++) {
            double integerPosition = PlaybackCoordinates.editorWorldZAtBeat(chart, wholeBeat) - 0.5;
            double previousTickPosition = PlaybackCoordinates.editorWorldZAtBeat(chart, wholeBeat - 1.0 / 12.0) - 0.5;
            double integerBeat = PlaybackCoordinates.editorBeatAtBlockZ(chart, integerPosition);
            double previousTickBeat = PlaybackCoordinates.editorBeatAtBlockZ(chart, previousTickPosition);
            var integerDisplay = cn.frkovo.rhythmcmaker.common.note.EditorBeat.fromValue(integerBeat, chart.divisionsPerBeat);
            var previousTickDisplay = cn.frkovo.rhythmcmaker.common.note.EditorBeat.fromValue(previousTickBeat, chart.divisionsPerBeat);
            require(integerDisplay.whole() == wholeBeat && integerDisplay.numerator() == 0
                            && integerDisplay.denominator() == 12,
                    "integer Beat keeps its exact grid position at " + wholeBeat);
            require(previousTickDisplay.whole() == wholeBeat - 1 && previousTickDisplay.numerator() == 11
                            && previousTickDisplay.denominator() == 12,
                    "the immediately preceding tick remains distinct from integer Beat " + wholeBeat);
        }
    }

    private static void verifySaveSnapshotPreservesFraction() {
        ChartManifest chart = new ChartManifest();
        ChartManifest.Note note = new ChartManifest.Note();
        note.id = "saved-fraction";
        note.beat = 2.5;
        note.editorBeat = new cn.frkovo.rhythmcmaker.common.note.EditorBeat(2, 2, 4);
        chart.notes.add(note);
        ChartManifest saved = cn.frkovo.rhythmcmaker.common.note.NoteEditorDraft.snapshot(chart);
        require(saved.notes.getFirst().editorBeat != null
                        && saved.notes.getFirst().editorBeat.denominator() == 4,
                "the real async-save snapshot must retain unreduced fractions for editor.rmcd");
        require(saved != chart && saved.notes.getFirst() != note, "save snapshot is detached from live editing");
    }

    private static void verifyGeometryAndOriginRoundTrip() {
        ChartManifest.Note note = new ChartManifest.Note();
        note.scaleX = 2.0; note.scaleY = 1.5; note.scaleZ = 3.0;
        note.rotationX = 35.0; note.rotationY = 25.0; note.rotationZ = -78.0;
        org.joml.Matrix4fc matrix = cn.frkovo.rhythmcmaker.common.note.NoteGeometry.editorTransform(note).getMatrix();
        net.minecraft.util.math.Box bounds = cn.frkovo.rhythmcmaker.common.note.NoteGeometry.transformedBounds(2.55, 66.65, -120.3, matrix);
        require(Math.abs(bounds.getCenter().x - 2.55) < 1.0e-5
                        && Math.abs(bounds.getCenter().y - 66.65) < 1.0e-5
                        && Math.abs(bounds.getCenter().z + 120.3) < 1.0e-5,
                "nonuniform scale and three-axis rotation preserve the visible model center");
        for (int corner = 0; corner < 8; corner++) {
            org.joml.Vector3f point = matrix.transformPosition(new org.joml.Vector3f(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1));
            require(bounds.expand(1.0e-5).contains(2.55 + point.x, 66.65 + point.y, -120.3 + point.z),
                    "rotated AABB contains every visible model corner");
        }
        for (double coordinate : new double[]{0.0, -3.25, 66.05, 0.123456789}) {
            double original = coordinate;
            for (int pass = 0; pass < 1000; pass++) coordinate = NoteEditRules.editorNoteCoordinate(NoteEditRules.editorDisplayCoordinate(coordinate));
            require(Math.abs(coordinate - original) < 1.0e-12, "repeated display/save coordinate conversion must not drift by half a block");
        }
    }

    private static void verifyEditingValidation() {
        ChartManifest chart = new ChartManifest(); chart.bpm = 120; chart.laneCount = 3; chart.trackHeight = 3;
        ChartManifest.Note note = createNote(chart, "historical", 0, 1.0, 4.123456789, 66.12789, 0, -1);
        var editing = cn.frkovo.rhythmcmaker.common.note.NoteEditingModel.getInstance();
        require(editing.setBeat(chart, note, new cn.frkovo.rhythmcmaker.common.note.EditorBeat(2, 2, 4)), "historical out-of-range X does not prevent unrelated Beat edits");
        require(note.x == 4.123456789 && note.y == 66.12789, "Beat edits preserve exact X and Y values");
        require(note.editorBeat.denominator() == 4, "editing retains an unreduced fraction");
        require(!editing.setPosition(chart, note, Double.NaN, 0.0, note.beat), "NaN coordinate is rejected");
        require(!editing.setPosition(chart, note, 0.0, Double.POSITIVE_INFINITY, note.beat), "infinite coordinate is rejected");
        require(!editing.setPosition(chart, note, 1.01, 0.0, note.beat), "non-step coordinate is rejected at business boundary");
        require(!editing.setPosition(chart, note, 1.05, 0.0, note.beat), "coordinate beyond current lane range is rejected");
        require(editing.setPosition(chart, note, 0.05, 0.0, note.beat), "dragged coordinate accepts exact 0.05 steps");
        require(note.editorBeat.denominator() == 4, "coordinate-only edit preserves literal Beat form");
        require(!editing.setBeat(chart, note, new cn.frkovo.rhythmcmaker.common.note.EditorBeat(0, 1, 1001)), "fraction denominator above 1000 is rejected");
        require(editing.setBeat(chart, note, new cn.frkovo.rhythmcmaker.common.note.EditorBeat(1, 999, 1000)), "denominator 1000 is accepted");
        require(!editing.setTransform(chart, note, cn.frkovo.rhythmcmaker.common.note.NoteTransformField.SCALE_X, 1.74), "off-step scale is rejected");
        require(!editing.setTransform(chart, note, cn.frkovo.rhythmcmaker.common.note.NoteTransformField.SCALE_X, Double.NaN), "nonfinite transform is rejected");
        require(editing.setTransform(chart, note, cn.frkovo.rhythmcmaker.common.note.NoteTransformField.SCALE_X, 1.7), "scale 0.1 step is accepted");
        require(editing.setTransform(chart, note, cn.frkovo.rhythmcmaker.common.note.NoteTransformField.ROTATION_Y, -72.5), "rotation 0.5 step is accepted");
        require(!editing.setTransform(chart, note, cn.frkovo.rhythmcmaker.common.note.NoteTransformField.ROTATION_Y, 180.5), "rotation bound is enforced");
        require(NoteEditRules.usesDisplayEntity(note), "transformed integer-position notes use display representation");
        require(editing.setType(chart, note, 2) && note.y == 65.0, "converting an ordinary note to HOLD pins relative Y to zero");
        require(!editing.setPosition(chart, note, note.x, 0.05, note.beat), "ordinary HOLD Y cannot be edited");
        require(editing.setType(chart, note, 1), "ordinary HOLD remains distinct from immutable HOLD-group type");
    }

    private static void verifyHoldLifecycleAndNativeRoundTrip() {
        ChartManifest chart = new ChartManifest(); chart.bpm = 120; chart.laneCount = 5;
        ChartManifest.Note head = createNote(chart, "group-head", 2, 2.0, -0.25, 65, 7, 3);
        ChartManifest.Note middle = createNote(chart, "real-middle", 2, 3.0, 0.9, 65, 7, 3);
        ChartManifest.Note tail = createNote(chart, "group-tail", 2, 4.0, 0.25, 65, 7, 3);
        var groups = cn.frkovo.rhythmcmaker.common.note.HoldGroupModel.getInstance();
        groups.reconcile(chart);
        require(HoldGroupRules.isEndpoint(chart, head) && HoldGroupRules.isEndpoint(chart, tail)
                        && !HoldGroupRules.isEndpoint(chart, middle), "only real Hold endpoints are editable");
        java.util.Set<String> fills = chart.notes.stream().filter(note -> note.visualOnly).map(note -> note.id).collect(java.util.stream.Collectors.toSet());
        require(!fills.isEmpty() && groups.runtimeNotes(chart).size() == 3, "visual fill never joins formal judgement notes");
        groups.reconcile(chart);
        require(fills.equals(chart.notes.stream().filter(note -> note.visualOnly).map(note -> note.id).collect(java.util.stream.Collectors.toSet())), "rebuilding preserves deterministic fill identities");
        JsonObject local = JsonParser.parseString(ChartStorage.serializeChartDocument(chart)).getAsJsonObject();
        require(local.getAsJsonArray("notes").size() == 3, "Maker rmcc does not persist visual-only fill as real notes");
        com.google.gson.Gson gson = new com.google.gson.Gson();
        ChartManifest reloaded = gson.fromJson(local.get("meta"), ChartManifest.class);
        for (var element : local.getAsJsonArray("notes")) reloaded.notes.add(gson.fromJson(element, ChartManifest.Note.class));
        groups.reconcile(reloaded);
        require(fills.equals(reloaded.notes.stream().filter(note -> note.visualOnly).map(note -> note.id).collect(java.util.stream.Collectors.toSet())), "reopening recreates the same continuous visual fill");
        String beforeLayout = Rhythmc3Exporter.trackJson(chart, new ChartManifest.Track(7), 8.0).toString();
        EditorTrackLayout.of(5, 16, 5).applyTo(chart);
        EditorNoteLayout.repositionNotes(chart);
        groups.reconcile(chart);
        require(beforeLayout.equals(Rhythmc3Exporter.trackJson(chart, new ChartManifest.Track(7), 8.0).toString()), "track width/division/height rebuild does not alter playback pose or Beat");
        var editing = cn.frkovo.rhythmcmaker.common.note.NoteEditingModel.getInstance();
        require(editing.setBeat(chart, head, new cn.frkovo.rhythmcmaker.common.note.EditorBeat(1, 3, 4)), "valid group head Beat can change");
        require(middle.x == 0.9, "editing a group endpoint does not overwrite imported interior geometry");
        require(!editing.setBeat(chart, head, new cn.frkovo.rhythmcmaker.common.note.EditorBeat(4, 0, 1)), "head/tail overlap is rejected");
        require(!editing.setType(chart, head, 0), "Hold-group endpoints cannot become ordinary note types");
        JsonObject official = Rhythmc3Exporter.trackJson(chart, new ChartManifest.Track(7), 8.0);
        require(official.getAsJsonArray("notes").size() == 3, "official export retains all real Hold members");
        for (var element : official.getAsJsonArray("notes")) {
            JsonObject exported = element.getAsJsonObject();
            require(exported.keySet().equals(java.util.Set.of("noteType", "beat", "pos", "scale", "rotation", "holdGroup")), "official notes contain exactly the native contract fields");
        }
        ChartManifest nativeReloaded = new ChartManifest(); nativeReloaded.bpm = chart.bpm;
        ChartStorage.importRhythmc3Track(nativeReloaded, ChartTiming.prepare(nativeReloaded), official);
        require(HoldGroupRules.members(nativeReloaded, 7, 3).size() == 3, "native export/import preserves the Hold group");
        require(HoldGroupRules.isEndpoint(nativeReloaded, nativeReloaded.notes.getFirst()), "native imported head resolves as an editable target");
        head.beat = tail.beat + 1.0;
        groups.initializeRoles(chart);
        require(!editing.canEdit(chart, head) && !editing.canEdit(chart, tail), "reversed explicit endpoint identities lock all numeric edits");
        require(HoldGroupRules.isEndpoint(chart, head), "invalid group remains selectable for whole-group deletion");
        require(cn.frkovo.rhythmcmaker.common.note.NoteSnapModel.getInstance().targetZ(chart, head) == head.z, "snap does not throw or silently reorder an invalid group");
        ChartManifest orphan = new ChartManifest();
        ChartManifest.Note orphanHead = createNote(orphan, "orphan", 2, 1, 0, 65, 0, 1);
        groups.initializeRoles(orphan);
        require(orphanHead.holdGroup == 1 && !editing.canEdit(orphan, orphanHead), "orphan group stays identifiable and locked, not converted into ordinary HOLD");
    }

    private static void verifySnapOrderAndTrackIsolation() {
        ChartManifest chart = new ChartManifest(); chart.bpm = 120;
        ChartManifest.Note head = createNote(chart, "snap-head", 2, 2.01, 0, 65, 2, 4);
        ChartManifest.Note tail = createNote(chart, "snap-tail", 2, 2.02, 0, 65, 2, 4);
        createNote(chart, "other-track-head", 2, 10.0, 0, 65, 3, 4);
        createNote(chart, "other-track-tail", 2, 11.0, 0, 65, 3, 4);
        cn.frkovo.rhythmcmaker.common.note.HoldGroupModel.getInstance().initializeRoles(chart);
        var snapping = cn.frkovo.rhythmcmaker.common.note.NoteSnapModel.getInstance();
        double headZ = snapping.targetZ(chart, head);
        double tailZ = snapping.targetZ(chart, tail);
        require(headZ > tailZ && headZ == Math.rint(headZ) && tailZ == Math.rint(tailZ), "short groups snap to distinct integer positions in forward Beat order");
        require(HoldGroupRules.otherEndpoint(chart, head) == tail, "same Hold group IDs on different tracks never cross-bind");
        head.z = headZ; tail.z = tailZ;
        head.beat = PlaybackCoordinates.editorBeatAtBlockZ(chart, headZ);
        tail.beat = PlaybackCoordinates.editorBeatAtBlockZ(chart, tailZ);
        require(!NoteEditRules.usesDisplayEntity(head) && !NoteEditRules.usesDisplayEntity(tail), "untransformed integer snapped endpoints become block notes and remain editable");
        require(HoldGroupRules.isEndpoint(chart, head) && "snap-head".equals(head.id), "snapping retains endpoint identity");
        java.util.List<ChartManifest.Note> removed = HoldGroupRules.members(chart, 2, 4);
        chart.notes.removeAll(removed);
        require(chart.notes.size() == 2 && chart.notes.stream().allMatch(note -> note.trackId == 3), "whole-group deletion does not erase another track's matching group ID");
    }

    private static void verifyWindowDefaults() {
        for (float[] viewport : new float[][]{{320, 240}, {640, 360}, {854, 480}, {1280, 720}, {1920, 1080}, {3840, 2160}, {4096, 768}, {768, 4096}}) {
            var size = cn.frkovo.rhythmcmaker.common.ui.NoteEditorWindowSize.defaultSize(viewport[0], viewport[1]);
            require(size.width() > 0 && size.height() > 0 && size.width() <= viewport[0] - 16 && size.height() <= viewport[1] - 16,
                    "first-use note menu remains within viewport on every aspect ratio");
            require((double) size.width() * size.height() <= (double) viewport[0] * viewport[1] / 3.0 + 0.5,
                    "only the initial default note-menu area is capped at one third");
        }
    }

    private static ChartManifest.Note createNote(ChartManifest chart, String id, int type, double beat, double x, double y, int trackId, int group) {
        ChartManifest.Note note = new ChartManifest.Note();
        note.id = id; note.type = type; note.beat = beat; note.x = x; note.y = y; note.trackId = trackId; note.holdGroup = group;
        note.time = ChartTiming.beatToSeconds(chart, beat);
        note.z = PlaybackCoordinates.editorWorldZAtBeat(chart, beat) - 0.5;
        chart.notes.add(note);
        return note;
    }

    private static ChartManifest importLegacy(JsonObject legacy) {
        JsonObject converted = ChartStorage.convertRhythmc2ToRhythmc3(legacy, "regression", 1.0, 0L, "", 4, 120.0);
        ChartManifest chart = new ChartManifest();
        chart.bpm = 120.0;
        ChartTiming.Prepared timing = ChartTiming.prepare(chart);
        ChartStorage.importRhythmc3Track(chart, timing, converted.getAsJsonArray("tracks").get(0).getAsJsonObject());
        return chart;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
