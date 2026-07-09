package com.zizazr.kjsgen.core;

import com.google.gson.JsonObject;
import com.zizazr.kjsgen.core.UndoStack.UndoAction;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure-logic tests for {@link UndoStack} — the project's history data structure. */
class UndoStackTest {

    private static UndoAction added(String uid) {
        JsonObject snap = new JsonObject();
        snap.addProperty("uid", uid);
        return new UndoAction.RecipeAdded(uid, snap);
    }

    @Test
    void emptyStackHasNothingToUndoOrRedo() {
        UndoStack stack = new UndoStack();
        assertFalse(stack.canUndo());
        assertFalse(stack.canRedo());
        assertTrue(stack.undo().isEmpty());
        assertTrue(stack.redo().isEmpty());
    }

    @Test
    void undoReturnsMostRecentActionAndEnablesRedo() {
        UndoStack stack = new UndoStack();
        UndoAction first = added("a");
        UndoAction second = added("b");
        stack.push(first);
        stack.push(second);

        assertTrue(stack.canUndo());
        assertFalse(stack.canRedo());

        assertSame(second, stack.undo().orElseThrow()); // LIFO: newest comes back first
        assertSame(first, stack.undo().orElseThrow());
        assertFalse(stack.canUndo());
        assertTrue(stack.canRedo());
    }

    @Test
    void redoReplaysInReverseOrder() {
        UndoStack stack = new UndoStack();
        UndoAction first = added("a");
        UndoAction second = added("b");
        stack.push(first);
        stack.push(second);
        stack.undo(); // second -> redo
        stack.undo(); // first -> redo

        assertSame(first, stack.redo().orElseThrow());
        assertSame(second, stack.redo().orElseThrow());
        assertFalse(stack.canRedo());
        assertTrue(stack.canUndo());
    }

    @Test
    void pushClearsPendingRedo() {
        UndoStack stack = new UndoStack();
        stack.push(added("a"));
        stack.undo();
        assertTrue(stack.canRedo());

        stack.push(added("b")); // a new action invalidates the redo branch
        assertFalse(stack.canRedo());
        assertTrue(stack.redo().isEmpty());
    }

    @Test
    void clearForgetsBothStacks() {
        UndoStack stack = new UndoStack();
        stack.push(added("a"));
        stack.undo();
        stack.push(added("b"));

        stack.clear();
        assertFalse(stack.canUndo());
        assertFalse(stack.canRedo());
    }

    @Test
    void depthIsCappedAtMaxDepthDroppingOldest() {
        UndoStack stack = new UndoStack();
        for (int i = 0; i < UndoStack.MAX_DEPTH + 25; i++) {
            stack.push(added("r" + i));
        }
        assertEquals(UndoStack.MAX_DEPTH, stack.undoDepth());

        // The newest action must survive the trim; the 25 oldest must have been dropped.
        Optional<UndoAction> newest = stack.undo();
        assertTrue(newest.isPresent());
        assertEquals("r" + (UndoStack.MAX_DEPTH + 24),
                ((UndoAction.RecipeAdded) newest.orElseThrow()).uid());
    }
}
