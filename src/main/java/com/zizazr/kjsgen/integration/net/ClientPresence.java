package com.zizazr.kjsgen.integration.net;

import com.zizazr.kjsgen.core.SlotContent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side view of who else is editing the current project and where their cursors are.
 *
 * <p>Fed by {@code OP_PRESENCE} (the viewer list) and {@code OP_CURSOR} (live positions) in
 * {@link ClientEditSession#handleServer}. The vanilla editor reads it every frame to draw the
 * remote cursors and the head-icon presence bar. All access happens on the client main thread.
 */
public final class ClientPresence {
    /** One operator viewing the same project. */
    public record Viewer(UUID uuid, String name, int color) {
    }

    /** A remote operator's last-known cursor, in panel-relative coordinates. */
    public static final class Cursor {
        public int x;
        public int y;
        public String state = "default";
        public boolean visible;
        /** The item this operator is carrying "in hand", or EMPTY. Drawn at their cursor. */
        public SlotContent held = SlotContent.EMPTY;
    }

    private static final List<Viewer> viewers = new ArrayList<>();
    /** uuid -> cursor; ordered so rendering is stable. */
    private static final Map<UUID, Cursor> cursors = new LinkedHashMap<>();
    /** uuid -> translation key of the kjsgen screen that operator is currently on. */
    private static final Map<UUID, String> screens = new LinkedHashMap<>();
    /** uuid -> uid of the recipe that operator currently has selected in the editor. */
    private static final Map<UUID, String> recipes = new LinkedHashMap<>();

    private ClientPresence() {
    }

    /** Replace the viewer list and drop cursors/screens/recipes for anyone no longer present. */
    public static void setViewers(List<Viewer> next) {
        viewers.clear();
        viewers.addAll(next);
        cursors.keySet().removeIf(id -> next.stream().noneMatch(v -> v.uuid().equals(id)));
        screens.keySet().removeIf(id -> next.stream().noneMatch(v -> v.uuid().equals(id)));
        recipes.keySet().removeIf(id -> next.stream().noneMatch(v -> v.uuid().equals(id)));
    }

    public static void updateScreen(UUID uuid, String screenKey) {
        screens.put(uuid, screenKey);
    }

    /** The screen an operator is on, or {@code null} if unknown. */
    public static String screen(UUID uuid) {
        return screens.get(uuid);
    }

    public static void updateRecipe(UUID uuid, String uid) {
        if (uid == null) {
            recipes.remove(uuid);
        } else {
            recipes.put(uuid, uid);
        }
    }

    /** The uid of the recipe an operator has open, or {@code null} if unknown/none. */
    public static String recipeUid(UUID uuid) {
        return recipes.get(uuid);
    }

    public static void updateCursor(UUID uuid, int x, int y, String state, SlotContent held) {
        Cursor c = cursors.computeIfAbsent(uuid, k -> new Cursor());
        c.x = x;
        c.y = y;
        c.state = state == null ? "default" : state;
        c.held = held == null ? SlotContent.EMPTY : held;
        c.visible = true;
    }

    /** Hide a remote cursor without dropping the operator from the presence bar. */
    public static void hideCursor(UUID uuid) {
        Cursor c = cursors.get(uuid);
        if (c != null) {
            c.visible = false;
        }
    }

    public static List<Viewer> viewers() {
        return viewers;
    }

    public static Cursor cursor(UUID uuid) {
        return cursors.get(uuid);
    }

    public static void clear() {
        viewers.clear();
        cursors.clear();
        screens.clear();
        recipes.clear();
    }
}
