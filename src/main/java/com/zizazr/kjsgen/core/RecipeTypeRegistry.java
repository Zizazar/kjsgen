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

    public static Collection<RecipeTypeDefinition> all() {
        return TYPES.values();
    }
}
