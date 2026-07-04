package com.zizazr.kjsgen.codegen.handlers;

import com.zizazr.kjsgen.codegen.JsUtil;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;

/**
 * {@code event.smithingTransform(result, template, base, addition)} or
 * {@code event.smithingTrim(template, base, addition)}.
 */
public class SmithingRecipeCodegen implements RecipeCodegen {
    private final boolean trim;

    public SmithingRecipeCodegen(boolean trim) {
        this.trim = trim;
    }

    @Override
    public String generate(RecipeInstance recipe, RecipeTypeDefinition type) {
        StringBuilder js = new StringBuilder();
        if (trim) {
            js.append("event.smithingTrim(");
        } else {
            js.append("event.smithingTransform(")
                    .append(JsUtil.output(recipe.slot("output"))).append(", ");
        }
        js.append(JsUtil.ingredient(recipe.slot("template"))).append(", ")
                .append(JsUtil.ingredient(recipe.slot("base"))).append(", ")
                .append(JsUtil.ingredient(recipe.slot("addition"))).append(")");
        ShapedRecipeCodegen.appendCommonSuffix(js, recipe);
        return js.toString();
    }
}
