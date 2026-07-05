package com.zizazr.kjsgen.codegen;

import com.zizazr.kjsgen.core.SlotContent;

/**
 * Helpers producing KubeJS 1.21.1 expressions for slot contents.
 */
public final class JsUtil {
    private JsUtil() {
    }

    /** Single-quoted JS string literal. */
    public static String quote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    /** Item id, with the SNBT component patch appended in vanilla bracket syntax when present. */
    private static String itemIdWithComponents(SlotContent content) {
        String id = content.id();
        String components = content.components();
        if (!components.isEmpty()) {
            if (!components.startsWith("[")) {
                components = "[" + components + "]";
            }
            id = id + components;
        }
        return id;
    }

    /**
     * Ingredient expression for inputs: {@code 'minecraft:stick'},
     * {@code '#minecraft:planks'}, {@code Fluid.of('minecraft:water', 500)} or
     * a fluid-tag ingredient object.
     */
    public static String ingredient(SlotContent content) {
        return switch (content.kind()) {
            case EMPTY -> "''";
            case ITEM -> quote(itemIdWithComponents(content));
            case ITEM_TAG -> quote("#" + content.id());
            case FLUID -> "Fluid.of(" + quote(content.id()) + ", " + content.amount() + ")";
            case FLUID_TAG -> "{ fluidTag: " + quote("#" + content.id()) + ", amount: " + content.amount() + " }";
            // kubejs-mekanism ChemicalStackIngredient string form: "AMOUNTx (id|#tag)"
            case CHEMICAL -> quote(content.amount() + "x " + content.id());
            case CHEMICAL_TAG -> quote(content.amount() + "x #" + content.id());
        };
    }

    /**
     * Result expression for outputs: {@code 'minecraft:stone'},
     * {@code '3x minecraft:stone'}, {@code Item.of(...)} for component data,
     * with {@code .withChance(...)} appended when a chance below 1 is set.
     */
    public static String output(SlotContent content) {
        String base = switch (content.kind()) {
            case EMPTY -> "''";
            case ITEM -> {
                if (!content.components().isEmpty()) {
                    yield "Item.of(" + quote(itemIdWithComponents(content))
                            + (content.count() > 1 ? ", " + content.count() : "") + ")";
                }
                yield quote((content.count() > 1 ? content.count() + "x " : "") + content.id());
            }
            case ITEM_TAG -> quote("#" + content.id()); // tags are not valid outputs; validator rejects this
            case FLUID -> "Fluid.of(" + quote(content.id()) + ", " + content.amount() + ")";
            case FLUID_TAG -> "{ fluidTag: " + quote("#" + content.id()) + ", amount: " + content.amount() + " }";
            // kubejs-mekanism ChemicalStack string form: "AMOUNTx id"
            case CHEMICAL -> quote(content.amount() + "x " + content.id());
            case CHEMICAL_TAG -> quote(content.amount() + "x #" + content.id()); // tags are not valid outputs; validator rejects this
        };
        if (content.chance() < 1.0f && content.kind() == com.zizazr.kjsgen.core.ContentKind.ITEM) {
            if (!base.startsWith("Item.of(")) {
                base = "Item.of(" + base + ")";
            }
            base = base + ".withChance(" + trimFloat(content.chance()) + ")";
        }
        return base;
    }

    /** Float without a trailing ".0", e.g. 0.35 / 2. */
    public static String trimFloat(float f) {
        if (f == Math.floor(f) && !Float.isInfinite(f)) {
            return Integer.toString((int) f);
        }
        return Float.toString(f);
    }
}
