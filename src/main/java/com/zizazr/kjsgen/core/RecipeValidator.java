package com.zizazr.kjsgen.core;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Real-time validation of recipes: missing required slots, malformed ids,
 * duplicate recipe ids inside a project.
 */
public final class RecipeValidator {
    public enum Severity {
        WARNING, ERROR
    }

    /**
     * @param slotKey slot the issue refers to, or empty for recipe-level issues
     * @param messageKey translation key; formatted with {@code args}
     */
    public record Issue(Severity severity, String slotKey, String messageKey, Object... args) {
    }

    private RecipeValidator() {
    }

    /** Validate a single recipe against its type definition. */
    public static List<Issue> validate(RecipeInstance recipe, RecipeTypeDefinition type) {
        List<Issue> issues = new ArrayList<>();
        for (SlotDefinition slot : type.slots()) {
            SlotContent content = recipe.slot(slot.key());
            if (slot.required() && content.isEmpty()) {
                issues.add(new Issue(Severity.ERROR, slot.key(), "kjsgen.validation.required_slot_empty"));
            }
            if (!content.isEmpty()) {
                if (!slot.accepts(content.kind())) {
                    issues.add(new Issue(Severity.ERROR, slot.key(), "kjsgen.validation.content_not_allowed"));
                }
                if (ResourceLocation.tryParse(content.id()) == null) {
                    issues.add(new Issue(Severity.ERROR, slot.key(), "kjsgen.validation.bad_id", content.id()));
                }
            }
        }
        // shaped crafting needs at least one input even though individual slots are optional
        if (type.slotsByRole(SlotRole.INPUT).stream().noneMatch(SlotDefinition::required)
                && type.slotsByRole(SlotRole.INPUT).stream().allMatch(s -> recipe.slot(s.key()).isEmpty())
                && !type.slotsByRole(SlotRole.INPUT).isEmpty()) {
            issues.add(new Issue(Severity.ERROR, "", "kjsgen.validation.no_inputs"));
        }
        if (!recipe.recipeId().isEmpty() && ResourceLocation.tryParse(recipe.recipeId()) == null) {
            issues.add(new Issue(Severity.ERROR, "", "kjsgen.validation.bad_recipe_id", recipe.recipeId()));
        }
        for (ParameterDefinition param : type.parameters()) {
            String value = recipe.param(type, param.key());
            if (!value.isEmpty() && !param.isValid(value)) {
                issues.add(new Issue(Severity.ERROR, "", "kjsgen.validation.bad_param", param.key(), value));
            }
        }
        return issues;
    }

    /** Project-level validation: duplicate explicit recipe ids. */
    public static List<Issue> validateProject(RecipeProject project) {
        List<Issue> issues = new ArrayList<>();
        Map<String, Integer> idCounts = new HashMap<>();
        for (RecipeInstance recipe : project.recipes()) {
            if (!recipe.recipeId().isEmpty()) {
                idCounts.merge(recipe.recipeId(), 1, Integer::sum);
            }
        }
        idCounts.forEach((id, n) -> {
            if (n > 1) {
                issues.add(new Issue(Severity.ERROR, "", "kjsgen.validation.duplicate_recipe_id", id, n));
            }
        });
        return issues;
    }

    public static boolean hasErrors(List<Issue> issues) {
        return issues.stream().anyMatch(i -> i.severity() == Severity.ERROR);
    }
}
