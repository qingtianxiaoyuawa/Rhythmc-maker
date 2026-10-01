package cn.frkovo.rhythmcv2.cv2.transport;

import cn.frkovo.rhythmcv2.cv2.core.BeatClock;
import cn.frkovo.rhythmcv2.cv2.core.BeatFraction;
import cn.frkovo.rhythmcv2.cv2.session.CharterSession;

import java.math.BigDecimal;

/**
 * 播放引擎（§10）：服务端 tick 主时钟驱动视觉；音频位置权威在 mod。
 * 每 tick：playingMs += 50×speed → currentBeat = clock.beat(ms(anchor) + playingMs)。
 * 每 10 tick 对账：|插件beat换算ms − mod STATE.positionMs| > 60ms ⇒ 重 seek mod。
 */
public final class Transport {

    public enum Mode {
        STOPPED, PLAYING, PAUSED
    }

    private final CharterSession session;
    private Mode mode = Mode.STOPPED;
    private BeatFraction anchorBeat = BeatFraction.ZERO;
    private long anchorMs;
    private long playingMs;
    private float speed = 1.0f;
    private int tickCounter = 0;
    /** 最近一次对账漂移（ms），HUD 显示用。 */
    private long lastDriftMs;

    public Transport(CharterSession session) {
        this.session = session;
    }

    public Mode mode() {
        return mode;
    }

    public boolean isPlaying() {
        return mode == Mode.PLAYING;
    }

    public float speed() {
        return speed;
    }

    public BeatFraction currentBeat() {
        if (mode == Mode.STOPPED) {
            return session.state().cursorBeat;
        }
        BeatClock clock = session.chart().clock();
        return clock.beat(anchorMs + playingMs);
    }

    public long lastDriftMs() {
        return lastDriftMs;
    }

    public void play() {
        play(session.state().cursorBeat);
    }

    public void play(BeatFraction from) {
        anchorBeat = from;
        anchorMs = session.chart().clock().msLong(from);
        playingMs = 0;
        mode = Mode.PLAYING;
        sendOp(buf -> {
            buf.writeDouble(anchorMs);
            buf.writeFloat(speed);
        }, CharterAudioBridge.OP_TRANSPORT_PLAY);
    }

    public void pause() {
        if (mode != Mode.PLAYING) {
            return;
        }
        session.state().cursorBeat = currentBeat();
        mode = Mode.PAUSED;
        sendOp(null, CharterAudioBridge.OP_TRANSPORT_PAUSE);
    }

    /** seek：插件 beat → ms → mod TRANSPORT_SEEK；视觉时钟直接跳变（断点播放核心）。 */
    public void seek(BeatFraction beat) {
        session.state().cursorBeat = beat;
        if (mode == Mode.PLAYING) {
            play(beat);
        } else {
            anchorBeat = beat;
            anchorMs = session.chart().clock().msLong(beat);
            playingMs = 0;
            sendOp(buf -> buf.writeDouble(anchorMs), CharterAudioBridge.OP_TRANSPORT_SEEK);
        }
    }

    public void stop() {
        if (mode != Mode.STOPPED) {
            seek(anchorBeat);
        }
        mode = Mode.STOPPED;
        sendOp(null, CharterAudioBridge.OP_TRANSPORT_STOP);
    }

    public void setSpeed(float s) {
        speed = Math.max(0.5f, Math.min(1.0f, s));
        if (mode == Mode.PLAYING) {
            play(currentBeat());
        } else {
            sendOp(buf -> buf.writeFloat(speed), CharterAudioBridge.OP_SET_SPEED);
        }
    }

    public void setLoop(BeatFraction a, BeatFraction b) {
        BeatClock clock = session.chart().clock();
        long aMs = clock.msLong(a);
        long bMs = clock.msLong(b);
        sendOp(buf -> {
            buf.writeDouble(aMs);
            buf.writeDouble(bMs);
        }, CharterAudioBridge.OP_SET_LOOP);
    }

    public void clearLoop() {
        sendOp(buf -> {
            buf.writeDouble(0);
            buf.writeDouble(-1);
        }, CharterAudioBridge.OP_SET_LOOP);
    }

    /** 每 tick 由会话 tick 任务调用。 */
    public void tick() {
        if (mode != Mode.PLAYING) {
            return;
        }
        playingMs += (long) (50 * speed);
        tickCounter++;
        // A-B 循环（beat 域，插件权威）
        var state = session.state();
        if (state.loopActive()) {
            BeatFraction cur = currentBeat();
            if (cur.compareTo(state.loopB) > 0) {
                seek(state.loopA);
                return;
            }
        }
        // 对账
        if (tickCounter % 10 == 0) {
            Long modPosition = session.bridge().lastReportedPositionMs(session.player());
            if (modPosition != null) {
                long pluginMs = session.chart().clock().msLong(currentBeat());
                long drift = Math.abs(pluginMs - modPosition);
                lastDriftMs = drift;
                if (drift > session.plugin().config().syncThresholdMs()) {
                    sendOp(buf -> buf.writeDouble(session.chart().clock().msLong(currentBeat())),
                            CharterAudioBridge.OP_TRANSPORT_SEEK);
                }
            }
        }
    }

    private void sendOp(CharterAudioBridge.PayloadWriter payload, int opcode) {
        session.bridge().send(session.player(), opcode, null, payload);
    }

    /** 供 HUD：当前 BPM 显示。 */
    public BigDecimal bpmNow() {
        return session.chart().clock().bpmAt(currentBeat());
    }

    public BeatFraction anchor() {
        return anchorBeat;
    }
}
