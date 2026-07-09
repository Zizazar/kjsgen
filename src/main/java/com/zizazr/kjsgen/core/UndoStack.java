package com.zizazr.kjsgen.core;

import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * A tiny, UI-agnostic undo/redo history of editor actions. It is a pure data
 * structure — it stores {@link UndoAction} records but never applies them; the
 * editor knows how to invert each action against its live model.
 *
 * <p>Each client keeps its own stack (undo is local, not shared on the server):
 * an undo only rolls back what this operator did in this editor session, and the
 * result is pushed to the server as an ordinary edit. See the "Undo/Redo" design
 * note for the collab reasoning.
 */
public final class UndoStack {
    /** How many actions to remember before the oldest are dropped. */
    static final int MAX_DEPTH = 100;

    private final Deque<UndoAction> undo = new ArrayDeque<>();
    private final Deque<UndoAction> redo = new ArrayDeque<>();

    /** Record a freshly performed action, invalidating any pending redo. */
    public void push(UndoAction action) {
        undo.push(action);
        redo.clear();
        trim();
    }

    /** Pop the most recent action so the caller can invert it; moves it to the redo stack. */
    public Optional<UndoAction> undo() {
        if (undo.isEmpty()) {
            return Optional.empty();
        }
        UndoAction action = undo.pop();
        redo.push(action);
        return Optional.of(action);
    }

    /** Pop the most recently undone action so the caller can re-apply it; moves it back to undo. */
    public Optional<UndoAction> redo() {
        if (redo.isEmpty()) {
            return Optional.empty();
        }
        UndoAction action = redo.pop();
        undo.push(action);
        return Optional.of(action);
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** Forget the whole history (e.g. when switching to a different project). */
    public void clear() {
        undo.clear();
        redo.clear();
    }

    int undoDepth() {
        return undo.size();
    }

    int redoDepth() {
        return redo.size();
    }

    private void trim() {
        while (undo.size() > MAX_DEPTH) {
            undo.removeLast(); // drop the oldest recorded action
        }
    }

    /**
     * One reversible editor action. Recipe snapshots use {@link RecipeInstance#toJson()} /
     * {@link RecipeInstance#fromJson(JsonObject)} so no extra serialization is needed; the uid
     * survives the round-trip. Every action stores enough to move in both directions, so undo and
     * redo are symmetric.
     */
    public sealed interface UndoAction {
        /** A recipe was edited in place (slot/param/meta field of the recipe). */
        record RecipeChanged(String uid, JsonObject before, JsonObject after) implements UndoAction {}

        /** A recipe was added (via the type picker, duplicate, or import). */
        record RecipeAdded(String uid, JsonObject snapshot) implements UndoAction {}

        /** A recipe was removed. */
        record RecipeRemoved(String uid, JsonObject snapshot) implements UndoAction {}

        /** The project meta fields (name / target file / export options) changed. */
        record ProjectMetaChanged(String beforeName, String beforeTargetFile,
                                  boolean beforeComments, boolean beforeReload,
                                  String afterName, String afterTargetFile,
                                  boolean afterComments, boolean afterReload) implements UndoAction {}
    }
}
