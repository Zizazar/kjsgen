package com.zizazr.kjsgen.codegen.handlers;

import com.zizazr.kjsgen.codegen.JsUtil;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;

/**
 * Shared handler for the four vanilla cooking recipe families:
 * {@code event.smelting(output, input).xp(0.7).cookingTime(100)}
 * (also blasting / smoking / campfireCooking).
 */
public class CookingRecipeCodegen implements RecipeCodegen {
    private final String method;
    private final int defaultTime;

    public CookingRecipeCodegen(String method, int defaultTime) {
        this.method = method;
        this.defaultTime = defaultTime;
    }

    @Override
    public String generate(RecipeInstance recipe, RecipeTypeDefinition type) {
        StringBuilder js = new StringBuilder();
        js.append("event.").append(method).append("(")
                .append(JsUtil.output(recipe.slot("output"))).append(", ")
                .append(JsUtil.ingredient(recipe.slot("input"))).append(")");
        float xp = recipe.paramFloat(type, "xp", 0.0f);
        if (xp > 0.0f) {
            js.append(".xp(").append(JsUtil.trimFloat(xp)).append(")");
        }
        int time = recipe.paramInt(type, "cookingTime", defaultTime);
        if (time != defaultTime) {
            js.append(".cookingTime(").append(time).append(")");
        }
        ShapedRecipeCodegen.appendCommonSuffix(js, recipe);
        return js.toString();
    }
}
