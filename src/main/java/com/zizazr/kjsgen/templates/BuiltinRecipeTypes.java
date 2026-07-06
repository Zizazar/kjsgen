package com.zizazr.kjsgen.templates;

import com.zizazr.kjsgen.core.LayoutDecoration;
import com.zizazr.kjsgen.core.ParameterDefinition;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import com.zizazr.kjsgen.core.SlotDefinition;
import com.zizazr.kjsgen.core.SlotRole;

import java.util.ArrayList;
import java.util.List;

/**
 * The built-in vanilla recipe types. Slot positions mirror the JEI category
 * layouts (18x18 slots, same relative arrangement) so the editor canvas looks
 * like the JEI screen the user already knows.
 */
public final class BuiltinRecipeTypes {
    private BuiltinRecipeTypes() {
    }

    public static void register() {
        RecipeTypeRegistry.register(shaped());
        RecipeTypeRegistry.register(shapeless());
        RecipeTypeRegistry.register(cooking("smelting", "minecraft:furnace", 0.1f, 200));
        RecipeTypeRegistry.register(cooking("blasting", "minecraft:blast_furnace", 0.1f, 100));
        RecipeTypeRegistry.register(cooking("smoking", "minecraft:smoker", 0.1f, 100));
        RecipeTypeRegistry.register(cooking("campfire_cooking", "minecraft:campfire", 0.0f, 600));
        RecipeTypeRegistry.register(stonecutting());
        RecipeTypeRegistry.register(smithingTransform());
        RecipeTypeRegistry.register(smithingTrim());
        RecipeTypeRegistry.register(brewing());
    }

    /** 3x3 crafting grid slots, keys in0..in8, with count editing for shapeless. */
    private static List<SlotDefinition> craftingGrid(boolean allowCount) {
        List<SlotDefinition> slots = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                slots.add(new SlotDefinition("in" + (row * 3 + col), SlotRole.INPUT,
                        col * 18, row * 18, false, true, true, false, allowCount, false));
            }
        }
        return slots;
    }

    private static RecipeTypeDefinition shaped() {
        List<SlotDefinition> slots = craftingGrid(false);
        slots.add(new SlotDefinition("output", SlotRole.OUTPUT, 94, 18, true, true, false, false, true, false));
        return new RecipeTypeDefinition("kjsgen:shaped", "minecraft", "minecraft:crafting_table",
                116, 54, List.copyOf(slots),
                List.of(LayoutDecoration.arrow(61, 19)),
                List.of(), "kjsgen:shaped", "", "", "minecraft:crafting");
    }

    private static RecipeTypeDefinition shapeless() {
        // A dynamic ingredient list ("in0", "in1", ...) instead of a fixed 3x3 grid:
        // one slot plus a '+' button, entries wrap into rows and auto-compact.
        List<SlotDefinition> slots = new ArrayList<>();
        slots.add(SlotDefinition.inputList("in", 0, 0, false, true));
        slots.add(new SlotDefinition("output", SlotRole.OUTPUT, 94, 18, true, true, false, false, true, false));
        return new RecipeTypeDefinition("kjsgen:shapeless", "minecraft", "minecraft:crafting_table",
                116, 54, List.copyOf(slots),
                List.of(LayoutDecoration.arrow(61, 19)),
                List.of(), "kjsgen:shapeless", "", "", "minecraft:crafting");
    }

    private static RecipeTypeDefinition cooking(String name, String icon, float defaultXp, int defaultTime) {
        return new RecipeTypeDefinition("kjsgen:" + name, "minecraft", icon,
                82, 38,
                List.of(
                        new SlotDefinition("input", SlotRole.INPUT, 0, 0, true, true, true, false, false, false),
                        new SlotDefinition("output", SlotRole.OUTPUT, 60, 8, true, true, false, false, true, false)
                ),
                List.of(
                        LayoutDecoration.flame(1, 20),
                        LayoutDecoration.arrow(24, 9)
                ),
                List.of(
                        ParameterDefinition.ofFloat("xp", defaultXp),
                        ParameterDefinition.ofInt("cookingTime", defaultTime)
                ),
                "kjsgen:" + name, "", "", "minecraft:" + name);
    }

    private static RecipeTypeDefinition stonecutting() {
        return new RecipeTypeDefinition("kjsgen:stonecutting", "minecraft", "minecraft:stonecutter",
                82, 34,
                List.of(
                        new SlotDefinition("input", SlotRole.INPUT, 0, 8, true, true, true, false, false, false),
                        new SlotDefinition("output", SlotRole.OUTPUT, 60, 8, true, true, false, false, true, false)
                ),
                List.of(LayoutDecoration.arrow(26, 9)),
                List.of(), "kjsgen:stonecutting", "", "", "minecraft:stonecutting");
    }

    private static RecipeTypeDefinition smithingTransform() {
        return new RecipeTypeDefinition("kjsgen:smithing_transform", "minecraft", "minecraft:smithing_table",
                116, 18,
                List.of(
                        new SlotDefinition("template", SlotRole.INPUT, 0, 0, true, true, true, false, false, false),
                        new SlotDefinition("base", SlotRole.INPUT, 18, 0, true, true, true, false, false, false),
                        new SlotDefinition("addition", SlotRole.INPUT, 36, 0, true, true, true, false, false, false),
                        new SlotDefinition("output", SlotRole.OUTPUT, 94, 0, true, true, false, false, false, false)
                ),
                List.of(LayoutDecoration.arrow(62, 1)),
                List.of(), "kjsgen:smithing_transform", "", "", "minecraft:smithing");
    }

    private static RecipeTypeDefinition smithingTrim() {
        return new RecipeTypeDefinition("kjsgen:smithing_trim", "minecraft", "minecraft:smithing_table",
                116, 18,
                List.of(
                        new SlotDefinition("template", SlotRole.INPUT, 0, 0, true, true, true, false, false, false),
                        new SlotDefinition("base", SlotRole.INPUT, 18, 0, true, true, true, false, false, false),
                        new SlotDefinition("addition", SlotRole.INPUT, 36, 0, true, true, true, false, false, false)
                ),
                List.of(
                        LayoutDecoration.arrow(62, 1),
                        LayoutDecoration.text(94, 5, "trim")
                ),
                List.of(), "kjsgen:smithing_trim", "", "", "minecraft:smithing");
    }

    private static RecipeTypeDefinition brewing() {
        return new RecipeTypeDefinition("kjsgen:brewing", "minecraft", "minecraft:brewing_stand",
                98, 61,
                List.of(
                        new SlotDefinition("ingredient", SlotRole.INPUT, 23, 2, true, true, true, false, false, false),
                        new SlotDefinition("bottle", SlotRole.INPUT, 23, 43, true, true, true, false, false, false),
                        new SlotDefinition("output", SlotRole.OUTPUT, 80, 21, true, true, false, false, false, false)
                ),
                List.of(LayoutDecoration.arrow(48, 22)),
                List.of(), "kjsgen:brewing", "morejs", "", "minecraft:brewing");
    }
}
