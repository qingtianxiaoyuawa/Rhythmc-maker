package cn.frkovo.rhythmcmaker.common.note;

import cn.frkovo.rhythmcmaker.chart.ChartManifest;
import net.minecraft.util.math.AffineTransformation;
import net.minecraft.util.math.Box;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class NoteGeometry {
    private NoteGeometry() {}

    public static Quaternionf rotation(ChartManifest.Note note) {
        return new Quaternionf().rotationXYZ((float) Math.toRadians(note.rotationX),
                (float) Math.toRadians(note.rotationY), (float) Math.toRadians(note.rotationZ));
    }

    public static AffineTransformation centeredTransform(Quaternionf rotation, Vector3f scale) {
        Vector3f translation = rotation.transform(new Vector3f(scale).mul(-0.5f));
        return new AffineTransformation(translation, rotation, scale, new Quaternionf());
    }

    public static AffineTransformation editorTransform(ChartManifest.Note note) {
        return centeredTransform(rotation(note), new Vector3f((float) note.scaleX, (float) note.scaleY, (float) note.scaleZ));
    }

    public static Box transformedBounds(double anchorX, double anchorY, double anchorZ, Matrix4fc transform) {
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        for (int corner = 0; corner < 8; corner++) {
            Vector3f point = transform.transformPosition(new Vector3f(corner & 1, (corner >> 1) & 1, (corner >> 2) & 1));
            minimumX = Math.min(minimumX, anchorX + point.x);
            minimumY = Math.min(minimumY, anchorY + point.y);
            minimumZ = Math.min(minimumZ, anchorZ + point.z);
            maximumX = Math.max(maximumX, anchorX + point.x);
            maximumY = Math.max(maximumY, anchorY + point.y);
            maximumZ = Math.max(maximumZ, anchorZ + point.z);
        }
        return new Box(minimumX, minimumY, minimumZ, maximumX, maximumY, maximumZ);
    }

    public static boolean hasTransform(ChartManifest.Note note) {
        return Math.abs(note.scaleX - 1.0) > 1.0e-6 || Math.abs(note.scaleY - 1.0) > 1.0e-6
                || Math.abs(note.scaleZ - 1.0) > 1.0e-6 || Math.abs(note.rotationX) > 1.0e-6
                || Math.abs(note.rotationY) > 1.0e-6 || Math.abs(note.rotationZ) > 1.0e-6;
    }

    public static double stepped(double value, double minimum, double maximum, double step) {
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite edit value");
        double result = Math.rint(value / step) * step;
        return Math.max(minimum, Math.min(maximum, result));
    }
}
