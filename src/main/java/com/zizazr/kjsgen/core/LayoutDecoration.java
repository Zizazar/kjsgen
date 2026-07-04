package com.zizazr.kjsgen.core;

import com.google.gson.JsonObject;

/**
 * Non-interactive decoration drawn on the recipe canvas, mimicking JEI visuals
 * (arrows, flames, plus signs, free text).
 *
 * @param type ARROW / FLAME / PLUS / TEXT
 * @param x    canvas x
 * @param y    canvas y
 * @param text only for TEXT decorations
 */
public record LayoutDecoration(Type type, int x, int y, String text) {
    public enum Type {
        /** JEI-style recipe arrow, 22x15 px. */
        ARROW,
        /** Furnace flame, 14x14 px. */
        FLAME,
        /** Plus sign (smithing), 13x13 px. */
        PLUS,
        /** Free label text. */
        TEXT
    }

    public static LayoutDecoration arrow(int x, int y) {
        return new LayoutDecoration(Type.ARROW, x, y, "");
    }

    public static LayoutDecoration flame(int x, int y) {
        return new LayoutDecoration(Type.FLAME, x, y, "");
    }

    public static LayoutDecoration plus(int x, int y) {
        return new LayoutDecoration(Type.PLUS, x, y, "");
    }

    public static LayoutDecoration text(int x, int y, String text) {
        return new LayoutDecoration(Type.TEXT, x, y, text);
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", type.name());
        json.addProperty("x", x);
        json.addProperty("y", y);
        if (!text.isEmpty()) json.addProperty("text", text);
        return json;
    }

    public static LayoutDecoration fromJson(JsonObject json) {
        return new LayoutDecoration(
                Type.valueOf(json.get("type").getAsString()),
                json.has("x") ? json.get("x").getAsInt() : 0,
                json.has("y") ? json.get("y").getAsInt() : 0,
                json.has("text") ? json.get("text").getAsString() : ""
        );
    }
}
