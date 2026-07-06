package com.zizazr.kjsgen.codegen.handlers;

import com.zizazr.kjsgen.codegen.JsUtil;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;

import java.util.Optional;

/**
 * {@code event.stonecutting('3x minecraft:stone_brick_slab', 'minecraft:stone_bricks')}
 */
public class StonecuttingRecipeCodegen implements RecipeCodegen {
    @Override
    public String generate(RecipeInstance recipe, RecipeTypeDefinition type) {
        StringBuilder js = new StringBuilder();
        js.append("event.stonecutting(")
                .append(JsUtil.output(recipe.slot("output"))).append(", ")
                .append(JsUtil.ingredient(recipe.slot("input"))).append(")");
        ShapedRecipeCodegen.appendCommonSuffix(js, recipe);
        return js.toString();
    }

    @Override
    public Optional<String> removeTypeId(RecipeInstance recipe, RecipeTypeDefinition type) {
        return Optional.of("stonecutting");
    }
}
