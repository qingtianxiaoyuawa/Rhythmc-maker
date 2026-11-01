package cn.frkovo.rhythmcmaker.client.playback;

import cn.frkovo.rhythmcmaker.chart.TrackPlayback;

public record DisplayPose(double x, double y, double z, double rotationX, double rotationY,
                          double rotationZ, double scaleX, double scaleY, double scaleZ) {
    public static DisplayPose from(double[] values) {
        if (values == null || values.length != 9) return null;
        return new DisplayPose(values[0], values[1], values[2], values[3], values[4], values[5], values[6], values[7], values[8]);
    }

    public static DisplayPose from(TrackPlayback.PoseBuffer pose) {
        return new DisplayPose(pose.x(), pose.y(), pose.z(), pose.rotationX(), pose.rotationY(), pose.rotationZ(),
                pose.scaleX(), pose.scaleY(), pose.scaleZ());
    }

    public boolean matches(TrackPlayback.PoseBuffer pose) {
        return close(x, pose.x()) && close(y, pose.y()) && close(z, pose.z())
                && close(rotationX, pose.rotationX()) && close(rotationY, pose.rotationY())
                && close(rotationZ, pose.rotationZ()) && close(scaleX, pose.scaleX())
                && close(scaleY, pose.scaleY()) && close(scaleZ, pose.scaleZ());
    }

    private static boolean close(double left, double right) { return Math.abs(left - right) <= 1.0E-5; }
}