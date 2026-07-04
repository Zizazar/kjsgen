package com.zizazr.kjsgen.api;

import com.zizazr.kjsgen.codegen.CodegenRegistry;
import com.zizazr.kjsgen.codegen.RecipeCodegen;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Addon API entry point. Fired on the mod bus during common setup; addon mods
 * listen to it and register their own recipe types and codegen handlers —
 * no changes to kjsgen itself required:
 *
 * <pre>{@code
 * modEventBus.addListener((RegisterRecipeTypesEvent e) -> {
 *     e.registerCodegen("mymod:press", new MyPressCodegen());
 *     e.registerType(new RecipeTypeDefinition("mymod:press", "mymod", "mymod:press", ...));
 * });
 * }</pre>
 *
 * Simple types can skip the Java codegen entirely and use the built-in
 * template codegen ({@code "kjsgen:template"}) driven by a {@code template}
 * parameter, or ship a JSON layout in {@code assets/<ns>/kjsgen_layouts/}.
 */
public class RegisterRecipeTypesEvent extends Event implements IModBusEvent {
    /** Register a recipe type shown in the recipe type picker. */
    public void registerType(RecipeTypeDefinition definition) {
        RecipeTypeRegistry.register(definition);
    }

    /** Register a codegen handler referenced by {@link RecipeTypeDefinition#codegenId()}. */
    public void registerCodegen(String id, RecipeCodegen codegen) {
        CodegenRegistry.register(id, codegen);
    }
}
