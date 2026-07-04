package com.zizazr.kjsgen.codegen.handlers;

import com.zizazr.kjsgen.codegen.JsUtil;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.SlotContent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code event.shaped('3x minecraft:paper', ['AB ', ' C '], { A: '#minecraft:planks', ... })}
 *
 * Grid slots are keyed {@code in0..in8} (row-major 3x3), output slot {@code output}.
 */
public class ShapedRecipeCodegen implements RecipeCodegen {
    private static final String LETTERS = "ABCDEFGHI";

    @Override
    public String generate(RecipeInstance recipe, RecipeTypeDefinition type) {
        SlotContent[][] grid = new SlotContent[3][3];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                grid[row][col] = recipe.slot("in" + (row * 3 + col));
            }
        }

        // trim empty border rows/columns while preserving the shape
        int minRow = 3, maxRow = -1, minCol = 3, maxCol = -1;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                if (!grid[row][col].isEmpty()) {
                    minRow = Math.min(minRow, row);
                    maxRow = Math.max(maxRow, row);
                    minCol = Math.min(minCol, col);
                    maxCol = Math.max(maxCol, col);
                }
            }
        }
        if (maxRow < 0) {
            minRow = maxRow = minCol = maxCol = 0;
        }

        Map<SlotContent, Character> keys = new LinkedHashMap<>();
        StringBuilder pattern = new StringBuilder();
        for (int row = minRow; row <= maxRow; row++) {
            if (row > minRow) pattern.append(", ");
            pattern.append('\'');
            for (int col = minCol; col <= maxCol; col++) {
                SlotContent content = grid[row][col];
                if (content.isEmpty()) {
                    pattern.append(' ');
                } else {
                    Character key = keys.computeIfAbsent(content, c -> LETTERS.charAt(keys.size()));
                    pattern.append(key);
                }
            }
            pattern.append('\'');
        }

        StringBuilder mapping = new StringBuilder();
        keys.forEach((content, key) -> {
            if (mapping.length() > 0) mapping.append(",");
            mapping.append("\n    ").append(key).append(": ").append(JsUtil.ingredient(content));
        });

        StringBuilder js = new StringBuilder();
        js.append("event.shaped(").append(JsUtil.output(recipe.slot("output")))
                .append(", [").append(pattern).append("], {")
                .append(mapping);
        if (!keys.isEmpty()) js.append("\n  ");
        js.append("})");
        appendCommonSuffix(js, recipe);
        return js.toString();
    }

    static void appendCommonSuffix(StringBuilder js, RecipeInstance recipe) {
        if (!recipe.group().isEmpty()) {
            js.append(".group(").append(JsUtil.quote(recipe.group())).append(")");
        }
        if (!recipe.recipeId().isEmpty()) {
            js.append(".id(").append(JsUtil.quote(recipe.recipeId())).append(")");
        }
    }
}
