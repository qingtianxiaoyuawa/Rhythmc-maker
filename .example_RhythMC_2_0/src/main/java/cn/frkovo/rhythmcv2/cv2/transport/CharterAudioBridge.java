package cn.frkovo.rhythmcv2.cv2.transport;

import cn.frkovo.rhythmcv2.cv2.Main;
import cn.frkovo.rhythmcv2.cv2.session.CharterSession;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * rhythmc:charter_audio 服务端端（§11，提案通道；用户已于 MVP 启动时确认新建该独立通道）。
 * 帧 = [int opcode][string sessionId][payload]；字符串 = int byteLength + UTF-8；大端固定宽度。
 * 指令仅对开启 Charter 会话的玩家生效；sessionId 不匹配的帧直接丢弃。
 */
public final class CharterAudioBridge implements PluginMessageListener {

    public static final String CHANNEL = "rhythmc:charter_audio";
    public static final int PROTOCOL_VERSION = 1;

    // M→P
    public static final int OP_HELLO = 1;
    // P→M
    public static final int OP_CHART_META = 2;
    public static final int OP_TRANSPORT_PLAY = 3;
    public static final int OP_TRANSPORT_PAUSE = 4;
    public static final int OP_TRANSPORT_SEEK = 5;
    public static final int OP_TRANSPORT_STOP = 6;
    public static final int OP_SET_LOOP = 7;
    public static final int OP_SET_SPEED = 8;
    public static final int OP_PING = 9;
    // P→M ack
    public static final int OP_HELLO_ACK = 101;
    // M→P
    public static final int OP_CHART_STATUS = 102;
    public static final int OP_STATE = 103;
    public static final int OP_PONG = 104;
    public static final int OP_ERROR = 105;

    /** mod 能力位。 */
    public static final int CAP_SEEK = 1;
    public static final int CAP_LOOP = 2;
    public static final int CAP_SPEED = 4;

    public record ModInfo(int protocol, String modVersion, int capabilities) {
    }

    private final Main plugin;
    private final Map<java.util.UUID, ModInfo> modReady = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Long> reportedPositionMs = new ConcurrentHashMap<>();
    private final Map<java.util.UUID, Boolean> reportedPlaying = new ConcurrentHashMap<>();

    public CharterAudioBridge(Main plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public void unregister() {
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL, this);
        modReady.clear();
        reportedPositionMs.clear();
        reportedPlaying.clear();
    }

    public boolean isModReady(Player player) {
        return modReady.containsKey(player.getUniqueId());
    }

    public ModInfo modInfo(Player player) {
        return modReady.get(player.getUniqueId());
    }

    public Long lastReportedPositionMs(Player player) {
        return reportedPositionMs.get(player.getUniqueId());
    }

    public void onQuit(Player player) {
        modReady.remove(player.getUniqueId());
        reportedPositionMs.remove(player.getUniqueId());
        reportedPlaying.remove(player.getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] bytes) {
        if (!CHANNEL.equals(channel)) {
            return;
        }
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes));
            int opcode = in.readInt();
            String sessionId = readString(in);
            switch (opcode) {
                case OP_HELLO -> handleHello(player, in);
                case OP_CHART_STATUS -> handleChartStatus(player, sessionId, in);
                case OP_STATE -> handleState(player, sessionId, in);
                case OP_PONG -> {
                }
                case OP_ERROR -> plugin.getLogger().warning("[charter_audio] mod error from "
                        + player.getName() + ": " + readString(in));
                default -> {
                }
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[charter_audio] bad frame from " + player.getName()
                    + ": " + e.getMessage());
        }
    }

    private void handleHello(Player player, DataInputStream in) throws IOException {
        int protocol = in.readInt();
        String modVersion = readString(in);
        int capabilities = in.readInt();
        if (protocol != PROTOCOL_VERSION) {
            sendAck(player, false, "", "");
            plugin.getLogger().warning("[charter_audio] protocol mismatch: mod="
                    + protocol + " server=" + PROTOCOL_VERSION);
            return;
        }
        modReady.put(player.getUniqueId(), new ModInfo(protocol, modVersion, capabilities));
        CharterSession session = plugin.sessions().get(player);
        String sessionId = session == null ? "" : session.sessionId();
        sendAck(player, true, plugin.getDescription().getVersion(), sessionId);
        if (session != null) {
            sendChartMeta(session);
            plugin.getLogger().info("[charter_audio] handshake ok: " + player.getName()
                    + " mod=" + modVersion + " caps=" + capabilities);
        }
    }

    private void handleChartStatus(Player player, String sessionId, DataInputStream in) throws IOException {
        CharterSession session = requireSession(player, sessionId);
        if (session == null) {
            return;
        }
        boolean ok = in.readBoolean();
        String reason = readString(in);
        String sha1 = readString(in);
        long lengthMs = in.readLong();
        session.onAudioStatus(ok, reason, sha1, lengthMs);
    }

    private void handleState(Player player, String sessionId, DataInputStream in) throws IOException {
        CharterSession session = requireSession(player, sessionId);
        if (session == null) {
            return;
        }
        boolean playing = in.readBoolean();
        double positionMs = in.readDouble();
        float speed = in.readFloat();
        reportedPlaying.put(player.getUniqueId(), playing);
        reportedPositionMs.put(player.getUniqueId(), (long) positionMs);
        session.onModState(playing, positionMs, speed);
    }

    private CharterSession requireSession(Player player, String sessionId) {
        CharterSession session = plugin.sessions().get(player);
        if (session == null) {
            return null;
        }
        if (sessionId == null || !sessionId.equals(session.sessionId())) {
            return null; // 丢弃不匹配帧（附录 D）
        }
        return session;
    }

    // ---- 发送 ----

    /** payload 写入器（允许 IOException）。 */
    public interface PayloadWriter {
        void write(DataOutputStream out) throws IOException;
    }

    public void send(Player player, int opcode, CharterSession session,
                     PayloadWriter payload) {
        if (!isModReady(player)) {
            return;
        }
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
            DataOutputStream out = new DataOutputStream(bos);
            out.writeInt(opcode);
            writeString(out, session == null ? "" : session.sessionId());
            if (payload != null) {
                payload.write(out);
            }
            player.sendPluginMessage(plugin, CHANNEL, bos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[charter_audio] send failed op=" + opcode + ": " + e.getMessage());
        }
    }

    private void sendAck(Player player, boolean ok, String serverVersion, String sessionId) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(64);
            DataOutputStream out = new DataOutputStream(bos);
            out.writeInt(OP_HELLO_ACK);
            writeString(out, ""); // ack 不带 sessionId（握手期）
            out.writeBoolean(ok);
            writeString(out, serverVersion);
            writeString(out, sessionId);
            player.sendPluginMessage(plugin, CHANNEL, bos.toByteArray());
        } catch (IOException e) {
            plugin.getLogger().warning("[charter_audio] ack failed: " + e.getMessage());
        }
    }

    /** CHART_META：会话打开/恢复时下发（songFolder + audioHint）。 */
    public void sendChartMeta(CharterSession session) {
        send(session.player(), OP_CHART_META, session, out -> {
            writeString(out, session.chart().songFolder);
            writeString(out, session.chart().manifestName);
            writeString(out, session.state().audioHint);
        });
    }

    // ---- 编码 ----

    public static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    public static String readString(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len <= 0) {
            return "";
        }
        byte[] bytes = new byte[len];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public String newSessionId() {
        return UUID.randomUUID().toString();
    }
}
