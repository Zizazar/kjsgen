package com.zizazr.kjsgen.core;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of all editable recipe types. Built-in types are registered at mod
 * construction; addon mods contribute through the {@code addon-api}
 * ({@link com.zizazr.kjsgen.api.RegisterRecipeTypesEvent}).
 */
public final class RecipeTypeRegistry {
    private static final Map<String, RecipeTypeDefinition> TYPES = new LinkedHashMap<>();

    private RecipeTypeRegistry() {
    }

    public static synchronized void register(RecipeTypeDefinition definition) {
        TYPES.put(definition.id(), definition);
    }

    public static Optional<RecipeTypeDefinition> get(String id) {
        return Optional.ofNullable(TYPES.get(id));
    }

    /**
     * Finds the registered type whose {@code jeiCategory} matches the given JEI
     * category UID (e.g. "create:pressing"). Used by the JEI "Edit in kjsgen"
     * button to map a shown recipe onto its hand-authored layout — returns empty
     * when no layout implements that category (so the button stays hidden).
     */
    public static Optional<RecipeTypeDefinition> getByJeiCategory(String jeiCategoryUid) {
        if (jeiCategoryUid == null || jeiCategoryUid.isEmpty()) {
            return Optional.empty();
        }
        for (RecipeTypeDefinition definition : TYPES.values()) {
            if (jeiCategoryUid.equals(definition.jeiCategory())) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }

    public static Collection<RecipeTypeDefinition> all() {
        return TYPES.values();
    }
}
