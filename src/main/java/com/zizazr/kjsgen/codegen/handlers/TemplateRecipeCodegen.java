package com.zizazr.kjsgen.codegen.handlers;

import com.zizazr.kjsgen.codegen.JsUtil;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.SlotRole;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Data-driven codegen for recipe types declared purely in JSON (addon layouts,
 * bundled Create/Mekanism templates). The generated statement comes from the
 * hidden {@code __template} parameter of the type definition, e.g.
 *
 * <pre>
 * event.recipes.create.pressing({slot:output}, {slot:input})
 * </pre>
 *
 * Placeholders:
 * <ul>
 *   <li>{@code {slot:KEY}} — the slot content as an ingredient (inputs/catalysts)
 *       or result expression (outputs)</li>
 *   <li>{@code {param:KEY}} — raw parameter value (numbers, booleans)</li>
 *   <li>{@code {param_str:KEY}} — parameter value as a quoted JS string</li>
 *   <li>{@code {id}} — the recipe id (quoted), {@code {group}} — the group (quoted)</li>
 *   <li>{@code {inputs}} / {@code {outputs}} — comma-joined list of all
 *       <b>filled</b> input/output slots (for machines with a variable number
 *       of results, e.g. Create crushing byproducts)</li>
 * </ul>
 * An optional hidden {@code __suffix_id} parameter set to "false" disables the
 * automatic {@code .id(...)} suffix for syntaxes that don't support it.
 */
public class TemplateRecipeCodegen implements RecipeCodegen {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(slot|param|param_str):([A-Za-z0-9_.\\-]+)}|\\{id}|\\{group}|\\{inputs}|\\{outputs}");

    @Override
    public String generate(RecipeInstance recipe, RecipeTypeDefinition type) {
        String template = recipe.param(type, "__template");
        if (template.isEmpty()) {
            // JEI-imported types expose the template as a visible, per-recipe editable parameter
            template = recipe.param(type, "template");
        }
        if (template.isEmpty()) {
            return "// kjsgen: recipe type " + type.id() + " has no __template parameter";
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder js = new StringBuilder();
        while (matcher.find()) {
            String replacement;
            if (matcher.group(1) == null) {
                replacement = switch (matcher.group()) {
                    case "{id}" -> JsUtil.quote(recipe.recipeId());
                    case "{group}" -> JsUtil.quote(recipe.group());
                    case "{inputs}" -> joinSlots(recipe, type, SlotRole.INPUT);
                    case "{outputs}" -> joinSlots(recipe, type, SlotRole.OUTPUT);
                    default -> matcher.group();
                };
            } else {
                String key = matcher.group(2);
                replacement = switch (matcher.group(1)) {
                    case "slot" -> {
                        boolean isOutput = type.slot(key).map(s -> s.role() == SlotRole.OUTPUT).orElse(false);
                        yield isOutput ? JsUtil.output(recipe.slot(key)) : JsUtil.ingredient(recipe.slot(key));
                    }
                    case "param" -> recipe.param(type, key);
                    case "param_str" -> JsUtil.quote(recipe.param(type, key));
                    default -> matcher.group();
                };
            }
            matcher.appendReplacement(js, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(js);
        if (!recipe.param(type, "__suffix_id").equals("false")) {
            ShapedRecipeCodegen.appendCommonSuffix(js, recipe);
        }
        return js.toString();
    }

    private static String joinSlots(RecipeInstance recipe, RecipeTypeDefinition type, SlotRole role) {
        return type.slotsByRole(role).stream()
                .map(slot -> recipe.slot(slot.key()))
                .filter(content -> !content.isEmpty())
                .map(content -> role == SlotRole.OUTPUT ? JsUtil.output(content) : JsUtil.ingredient(content))
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }
}
