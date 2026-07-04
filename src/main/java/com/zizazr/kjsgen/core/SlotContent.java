package com.zizazr.kjsgen.core;

import com.google.gson.JsonObject;

import java.util.Objects;

/**
 * Content of a single slot in a {@link RecipeInstance}: an item, an item tag,
 * a fluid (tag), or nothing. Immutable value object.
 */
public final class SlotContent {
    public static final SlotContent EMPTY = new SlotContent(ContentKind.EMPTY, "", 1, 1000, 1.0f, "");

    private final ContentKind kind;
    /** Registry id ("minecraft:stick") or tag id without '#' ("minecraft:planks"). */
    private final String id;
    /** Stack count for items. */
    private final int count;
    /** Amount in mB for fluids. */
    private final int amount;
    /** Output chance, 1.0 = guaranteed. Only meaningful for outputs of types that support it. */
    private final float chance;
    /** Optional data components / NBT in SNBT notation, e.g. "{custom_name:'\"Foo\"'}" or "[minecraft:damage=5]". */
    private final String components;

    public SlotContent(ContentKind kind, String id, int count, int amount, float chance, String components) {
        this.kind = kind;
        this.id = id == null ? "" : id.trim();
        this.count = Math.max(1, count);
        this.amount = Math.max(1, amount);
        this.chance = Math.min(1.0f, Math.max(0.0f, chance));
        this.components = components == null ? "" : components.trim();
    }

    public static SlotContent item(String id, int count) {
        return new SlotContent(ContentKind.ITEM, id, count, 1000, 1.0f, "");
    }

    public static SlotContent itemTag(String tagId) {
        return new SlotContent(ContentKind.ITEM_TAG, stripHash(tagId), 1, 1000, 1.0f, "");
    }

    public static SlotContent fluid(String id, int amountMb) {
        return new SlotContent(ContentKind.FLUID, id, 1, amountMb, 1.0f, "");
    }

    public static SlotContent fluidTag(String tagId, int amountMb) {
        return new SlotContent(ContentKind.FLUID_TAG, stripHash(tagId), 1, amountMb, 1.0f, "");
    }

    private static String stripHash(String s) {
        return s != null && s.startsWith("#") ? s.substring(1) : s;
    }

    public ContentKind kind() {
        return kind;
    }

    public String id() {
        return id;
    }

    public int count() {
        return count;
    }

    public int amount() {
        return amount;
    }

    public float chance() {
        return chance;
    }

    public String components() {
        return components;
    }

    public boolean isEmpty() {
        return kind == ContentKind.EMPTY || id.isEmpty();
    }

    public SlotContent withCount(int newCount) {
        return new SlotContent(kind, id, newCount, amount, chance, components);
    }

    public SlotContent withAmount(int newAmount) {
        return new SlotContent(kind, id, count, newAmount, chance, components);
    }

    public SlotContent withChance(float newChance) {
        return new SlotContent(kind, id, count, amount, newChance, components);
    }

    public SlotContent withComponents(String newComponents) {
        return new SlotContent(kind, id, count, amount, chance, newComponents);
    }

    /** Human-readable short description, used in lists and tooltips. */
    public String describe() {
        if (isEmpty()) {
            return "-";
        }
        return switch (kind) {
            case ITEM -> (count > 1 ? count + "x " : "") + id;
            case ITEM_TAG -> "#" + id;
            case FLUID -> amount + "mB " + id;
            case FLUID_TAG -> amount + "mB #" + id;
            case EMPTY -> "-";
        };
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("kind", kind.name());
        json.addProperty("id", id);
        if (count != 1) json.addProperty("count", count);
        if (amount != 1000) json.addProperty("amount", amount);
        if (chance < 1.0f) json.addProperty("chance", chance);
        if (!components.isEmpty()) json.addProperty("components", components);
        return json;
    }

    public static SlotContent fromJson(JsonObject json) {
        ContentKind kind;
        try {
            kind = ContentKind.valueOf(json.has("kind") ? json.get("kind").getAsString() : "EMPTY");
        } catch (IllegalArgumentException e) {
            kind = ContentKind.EMPTY;
        }
        String id = json.has("id") ? json.get("id").getAsString() : "";
        int count = json.has("count") ? json.get("count").getAsInt() : 1;
        int amount = json.has("amount") ? json.get("amount").getAsInt() : 1000;
        float chance = json.has("chance") ? json.get("chance").getAsFloat() : 1.0f;
        String components = json.has("components") ? json.get("components").getAsString() : "";
        return new SlotContent(kind, id, count, amount, chance, components);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SlotContent that)) return false;
        return count == that.count && amount == that.amount
                && Float.compare(chance, that.chance) == 0
                && kind == that.kind && id.equals(that.id) && components.equals(that.components);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, id, count, amount, chance, components);
    }

    @Override
    public String toString() {
        return describe();
    }
}
