package com.zizazr.kjsgen.codegen.handlers;

import com.zizazr.kjsgen.codegen.JsUtil;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.SlotContent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code event.shapeless('minecraft:cake', ['minecraft:milk_bucket', '#minecraft:eggs'])}
 */
public class ShapelessRecipeCodegen implements RecipeCodegen {
    @Override
    public String generate(RecipeInstance recipe, RecipeTypeDefinition type) {
        List<String> ingredients = new ArrayList<>();
        for (SlotContent content : recipe.listSlots("in")) {
            if (!content.isEmpty()) {
                // shapeless inputs may carry a count: repeat the ingredient
                for (int n = 0; n < Math.max(1, content.count()); n++) {
                    ingredients.add(JsUtil.ingredient(content.withCount(1)));
                }
            }
        }
        StringBuilder js = new StringBuilder();
        js.append("event.shapeless(").append(JsUtil.output(recipe.slot("output")))
                .append(", [").append(String.join(", ", ingredients)).append("])");
        ShapedRecipeCodegen.appendCommonSuffix(js, recipe);
        return js.toString();
    }

    @Override
    public Optional<String> removeTypeId(RecipeInstance recipe, RecipeTypeDefinition type) {
        return Optional.of("crafting_shapeless");
    }
}
