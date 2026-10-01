package cn.frkovo.rhythmcv2.cv2.ops;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * undo/redo 不删除日志：undo/redo 各自应用逆操作，并作为新的操作追加日志（§13.1）。
 * 快照仅用于崩溃恢复，不作为撤销机制。
 */
public final class UndoManager {

    public record UndoEntry(Operation applied, Operation inverse) {
    }

    private final Deque<UndoEntry> undoStack = new ArrayDeque<>();
    private final Deque<UndoEntry> redoStack = new ArrayDeque<>();

    /** 正常编辑：应用成功后入栈，清空 redo。 */
    public void pushApplied(Operation applied, Operation inverse) {
        undoStack.push(new UndoEntry(applied, inverse));
        redoStack.clear();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public UndoEntry popUndo() {
        return undoStack.pop();
    }

    public void pushRedone(UndoEntry entry) {
        redoStack.push(entry);
    }

    public UndoEntry popRedo() {
        return redoStack.pop();
    }

    public void pushUndone(Operation applied, Operation inverse) {
        undoStack.push(new UndoEntry(applied, inverse));
    }

    public void clear() {
        undoStack.clear();
        redoStack.clear();
    }

    public int undoDepth() {
        return undoStack.size();
    }
}
