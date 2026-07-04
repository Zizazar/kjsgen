package com.zizazr.kjsgen.codegen;

import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;

/**
 * Turns one {@link RecipeInstance} into a KubeJS statement. One handler per
 * recipe type family; addon mods register theirs through the addon API.
 */
@FunctionalInterface
public interface RecipeCodegen {
    /**
     * @return a single JS statement (may span multiple lines), without
     *         indentation and without a trailing newline, e.g.
     *         {@code event.smelting('minecraft:iron_ingot', '#c:ores/iron').xp(0.7)}
     */
    String generate(RecipeInstance recipe, RecipeTypeDefinition type);

    /**
     * The KubeJS event wrapper the generated statements must live in. Recipes
     * with different wrappers end up in separate blocks of the exported file
     * (e.g. brewing via MoreJS uses a different event than regular recipes).
     */
    default String wrapperHeader() {
        return "ServerEvents.recipes(event => {";
    }

    default String wrapperFooter() {
        return "})";
    }
}
