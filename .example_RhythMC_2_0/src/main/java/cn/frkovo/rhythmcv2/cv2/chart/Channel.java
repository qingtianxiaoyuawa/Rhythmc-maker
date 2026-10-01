package cn.frkovo.rhythmcv2.cv2.chart;

/** 轨道事件通道：与 Reborn Track record 的十个通道一一对应，name 为 .rmcc JSON 字段名。 */
public enum Channel {
    SPEED("speedEvents"),
    X_TRANSFORM("xTransformEvents"),
    Y_TRANSFORM("yTransformEvents"),
    Z_TRANSFORM("zTransformEvents"),
    X_ROTATE("xRotateEvents"),
    Y_ROTATE("yRotateEvents"),
    Z_ROTATE("zRotateEvents"),
    X_SCALE("xScaleEvents"),
    Y_SCALE("yScaleEvents"),
    Z_SCALE("zScaleEvents");

    public final String json;

    Channel(String json) {
        this.json = json;
    }

    /** 通道默认值：transform/rotate = 0，scale = 1。 */
    public String defaultValue() {
        return this == X_SCALE || this == Y_SCALE || this == Z_SCALE ? "1" : "0";
    }
}
