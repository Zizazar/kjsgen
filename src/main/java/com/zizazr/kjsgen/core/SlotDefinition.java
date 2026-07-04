package com.zizazr.kjsgen.core;

import com.google.gson.JsonObject;

/**
 * Static definition of one slot in a recipe layout: where it sits on the canvas,
 * what it may contain and whether it must be filled.
 *
 * @param key        unique key inside the recipe type, e.g. "input0", "output", "template"
 * @param role       input/output/catalyst
 * @param x          canvas x position (JEI-style coordinates, slot is 18x18 px)
 * @param y          canvas y position
 * @param required   validation fails when a required slot is empty
 * @param allowsItem slot accepts items
 * @param allowsTag  slot accepts item/fluid tags (inputs usually do, outputs don't)
 * @param allowsFluid slot accepts fluids
 * @param allowsCount user may edit the stack count (usually only outputs / shapeless inputs)
 * @param allowsChance user may set an output chance (machine byproducts etc.)
 */
public record SlotDefinition(
        String key,
        SlotRole role,
        int x,
        int y,
        boolean required,
        boolean allowsItem,
        boolean allowsTag,
        boolean allowsFluid,
        boolean allowsCount,
        boolean allowsChance
) {
    public static SlotDefinition input(String key, int x, int y, boolean required) {
        return new SlotDefinition(key, SlotRole.INPUT, x, y, required, true, true, false, false, false);
    }

    public static SlotDefinition output(String key, int x, int y, boolean required) {
        return new SlotDefinition(key, SlotRole.OUTPUT, x, y, required, true, false, false, true, false);
    }

    public boolean accepts(ContentKind kind) {
        return switch (kind) {
            case EMPTY -> true;
            case ITEM -> allowsItem;
            case ITEM_TAG -> allowsItem && allowsTag;
            case FLUID -> allowsFluid;
            case FLUID_TAG -> allowsFluid && allowsTag;
        };
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("key", key);
        json.addProperty("role", role.name());
        json.addProperty("x", x);
        json.addProperty("y", y);
        json.addProperty("required", required);
        json.addProperty("item", allowsItem);
        json.addProperty("tag", allowsTag);
        json.addProperty("fluid", allowsFluid);
        json.addProperty("count", allowsCount);
        json.addProperty("chance", allowsChance);
        return json;
    }

    public static SlotDefinition fromJson(JsonObject json) {
        return new SlotDefinition(
                json.get("key").getAsString(),
                SlotRole.valueOf(json.has("role") ? json.get("role").getAsString() : "INPUT"),
                json.has("x") ? json.get("x").getAsInt() : 0,
                json.has("y") ? json.get("y").getAsInt() : 0,
                json.has("required") && json.get("required").getAsBoolean(),
                !json.has("item") || json.get("item").getAsBoolean(),
                json.has("tag") && json.get("tag").getAsBoolean(),
                json.has("fluid") && json.get("fluid").getAsBoolean(),
                json.has("count") && json.get("count").getAsBoolean(),
                json.has("chance") && json.get("chance").getAsBoolean()
        );
    }
}
