package com.zizazr.kjsgen.integration.kubejs;

import com.zizazr.kjsgen.codegen.CodegenRegistry;
import com.zizazr.kjsgen.codegen.JsUtil;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.ContentKind;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.core.SlotRole;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds the JS text for a set of recipes: groups them by their event wrapper
 * ({@code ServerEvents.recipes}, MoreJS brewing, ...), applies per-recipe
 * comments and {@code Platform.isLoaded} conditions.
 */
public final class ScriptAssembler {
    public static final String INDENT = "  ";

    private ScriptAssembler() {
    }

    /** JS text of one export block (without the kjsgen start/end markers). */
    public static String assemble(RecipeProject project, List<RecipeInstance> recipes) {
        // group by wrapper, preserving recipe order
        Map<String, StringBuilder> byWrapper = new LinkedHashMap<>();
        Map<String, String> footers = new LinkedHashMap<>();
        for (RecipeInstance recipe : recipes) {
            RecipeTypeDefinition type = RecipeTypeRegistry.get(recipe.typeId()).orElse(null);
            if (type == null) {
                continue;
            }
            RecipeCodegen codegen = CodegenRegistry.get(type.codegenId()).orElse(null);
            if (codegen == null) {
                continue;
            }
            StringBuilder body = byWrapper.computeIfAbsent(codegen.wrapperHeader(), h -> new StringBuilder());
            footers.put(codegen.wrapperHeader(), codegen.wrapperFooter());

            String statement = codegen.generate(recipe, type);
            String condition = effectiveCondition(recipe, type);
            String indent = INDENT;
            if (!condition.isEmpty()) {
                body.append(indent).append("if (Platform.isLoaded('").append(condition).append("')) {\n");
                indent = INDENT + INDENT;
            }
            if (project.exportComments()) {
                String comment = recipe.comment().isEmpty() ? recipe.describe() : recipe.comment();
                for (String line : comment.split("\n")) {
                    body.append(indent).append("// ").append(line).append("\n");
                }
            }
            if (recipe.replaceRecipe()) {
                String removeIndent = indent;
                removeStatement(recipe, type, codegen)
                        .ifPresent(line -> body.append(removeIndent).append(line).append("\n"));
            }
            for (String line : statement.split("\n")) {
                body.append(indent).append(line).append("\n");
            }
            if (!condition.isEmpty()) {
                body.append(INDENT).append("}\n");
            }
        }

        StringBuilder script = new StringBuilder();
        byWrapper.forEach((header, body) -> {
            if (script.length() > 0) {
                script.append("\n");
            }
            script.append(header).append("\n");
            script.append(body);
            script.append(footers.get(header)).append("\n");
        });
        return script.toString();
    }

    /**
     * {@code event.remove({ output: '...', type: '...' })} for the recipe's primary output,
     * so re-exporting the same recipe replaces it instead of creating a duplicate. Only emitted
     * when the codegen handler knows the recipe's bare KubeJS type id and the output is a plain
     * item (chemical/fluid outputs aren't reliably matched by the {@code output} filter).
     */
    private static Optional<String> removeStatement(RecipeInstance recipe, RecipeTypeDefinition type,
                                                      RecipeCodegen codegen) {
        Optional<String> removeType = codegen.removeTypeId(recipe, type);
        if (removeType.isEmpty()) {
            return Optional.empty();
        }
        SlotContent output = type.slotsByRole(SlotRole.OUTPUT).stream()
                .map(slot -> recipe.slot(slot.key()))
                .filter(content -> !content.isEmpty())
                .findFirst()
                .orElse(SlotContent.EMPTY);
        if (output.isEmpty() || output.kind() != ContentKind.ITEM) {
            return Optional.empty();
        }
        return Optional.of("event.remove({ output: " + JsUtil.quote(output.id()) + " })");
    }

    private static String effectiveCondition(RecipeInstance recipe, RecipeTypeDefinition type) {
        if (!recipe.conditionModLoaded().isEmpty()) {
            return recipe.conditionModLoaded();
        }
        // addon recipe types know which mod their syntax needs
        if (!type.requiresMod().isEmpty() && !type.requiresMod().equals("minecraft")
                && !type.requiresMod().equals("morejs")) {
            return type.requiresMod();
        }
        return "";
    }

    /** Preview text for a single recipe (used by the code preview panel). */
    public static String previewSingle(RecipeProject project, RecipeInstance recipe) {
        return assemble(project, List.of(recipe));
    }
}
