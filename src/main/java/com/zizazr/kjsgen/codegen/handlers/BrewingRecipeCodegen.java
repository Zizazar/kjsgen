package com.zizazr.kjsgen.codegen.handlers;

import com.zizazr.kjsgen.codegen.JsUtil;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;

/**
 * Brewing stand recipes are not datapack recipes in vanilla; KubeJS needs the
 * MoreJS addon for them. Generates
 * {@code event.addCustomBrewing(ingredient, bottle, result)} inside a
 * {@code MoreJSEvents.registerPotionBrewing} wrapper.
 */
public class BrewingRecipeCodegen implements RecipeCodegen {
    @Override
    public String generate(RecipeInstance recipe, RecipeTypeDefinition type) {
        return "event.addCustomBrewing(" +
                JsUtil.ingredient(recipe.slot("ingredient")) + ", " +
                JsUtil.ingredient(recipe.slot("bottle")) + ", " +
                JsUtil.output(recipe.slot("output")) + ")";
    }

    @Override
    public String wrapperHeader() {
        return "// Brewing recipes require the MoreJS addon (https://kubejs.com/wiki/addons/morejs)\n"
                + "MoreJSEvents.registerPotionBrewing(event => {";
    }
}
