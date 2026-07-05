package com.zizazr.kjsgen.codegen.handlers;

import com.zizazr.kjsgen.codegen.JsUtil;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.CreateSpecialModel;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.SlotContent;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Create mechanical crafting:
 * {@code event.recipes.create.mechanical_crafting('output', ['pattern', ...], { A: '...' })}.
 *
 * <p>Reads the resizable {@code gridW x gridH} grid (cells keyed {@code cell0..})
 * from {@link CreateSpecialModel}, trims empty border rows/columns and assigns a
 * distinct pattern character to each distinct ingredient — exactly like the
 * vanilla shaped generator, but for an arbitrary grid up to 9x9.
 */
public class CreateMechanicalCraftingCodegen implements RecipeCodegen {
    private static final String KEYS =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    @Override
    public String generate(RecipeInstance recipe, RecipeTypeDefinition type) {
        int w = CreateSpecialModel.gridW(recipe, type);
        int h = CreateSpecialModel.gridH(recipe, type);
        SlotContent[][] grid = new SlotContent[h][w];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                grid[row][col] = recipe.slot(CreateSpecialModel.cellKey(row, col));
            }
        }

        int minRow = h, maxRow = -1, minCol = w, maxCol = -1;
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
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
            if (row > minRow) {
                pattern.append(", ");
            }
            pattern.append('\'');
            for (int col = minCol; col <= maxCol; col++) {
                SlotContent content = grid[row][col];
                if (content.isEmpty()) {
                    pattern.append(' ');
                } else {
                    Character key = keys.computeIfAbsent(content,
                            c -> KEYS.charAt(Math.min(keys.size(), KEYS.length() - 1)));
                    pattern.append(key);
                }
            }
            pattern.append('\'');
        }

        StringBuilder mapping = new StringBuilder();
        keys.forEach((content, key) -> {
            if (mapping.length() > 0) {
                mapping.append(",");
            }
            mapping.append("\n    ").append(key).append(": ").append(JsUtil.ingredient(content));
        });

        StringBuilder js = new StringBuilder();
        js.append("event.recipes.create.mechanical_crafting(")
                .append(JsUtil.output(recipe.slot("output")))
                .append(", [").append(pattern).append("], {")
                .append(mapping);
        if (!keys.isEmpty()) {
            js.append("\n  ");
        }
        js.append("})");
        ShapedRecipeCodegen.appendCommonSuffix(js, recipe);
        return js.toString();
    }
}
