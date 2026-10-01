package cn.frkovo.rhythmcv2.cv2.ops;

import cn.frkovo.rhythmcv2.cv2.chart.EditorChart;
import cn.frkovo.rhythmcv2.cv2.draft.DraftCodec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 模型哈希：对 draft 模型 JSON 规范序列化做 SHA-256，用于操作日志 beforeHash/afterHash 链。 */
public final class ModelHash {

    private ModelHash() {
    }

    public static String of(EditorChart chart) {
        String json = DraftCodec.modelJson(chart);
        return sha256(json);
    }

    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
