package com.zizazr.kjsgen.ui;

import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import net.minecraft.network.chat.Component;

/**
 * Small helpers for UI texts.
 */
public final class UiTexts {
    private UiTexts() {
    }

    /** Display name of a recipe type with a readable fallback for unlocalized addon types. */
    public static Component typeName(RecipeTypeDefinition type) {
        String path = type.id().contains(":") ? type.id().substring(type.id().indexOf(':') + 1) : type.id();
        String fallback = Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
        return Component.translatableWithFallback(type.translationKey(), fallback);
    }

    public static Component paramName(String key) {
        String fallback = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        return Component.translatableWithFallback("kjsgen.param." + key, fallback);
    }
}
