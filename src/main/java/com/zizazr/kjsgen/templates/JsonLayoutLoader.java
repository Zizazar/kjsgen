package com.zizazr.kjsgen.templates;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads data-driven recipe type definitions from
 * {@code assets/<namespace>/kjsgen_layouts/*.json} of every loaded resource
 * pack / mod jar. This is the no-code half of the addon API: a mod (or a
 * resource pack) can add new recipe types with the built-in template codegen
 * without writing any Java.
 */
public final class JsonLayoutLoader extends SimplePreparableReloadListener<Map<ResourceLocation, JsonObject>> {
    public static final String FOLDER = "kjsgen_layouts";

    @Override
    protected Map<ResourceLocation, JsonObject> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonObject> jsons = new HashMap<>();
        resourceManager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).forEach((location, resource) -> {
            try (InputStreamReader reader = new InputStreamReader(resource.open(), StandardCharsets.UTF_8)) {
                jsons.put(location, JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception e) {
                KjsGen.LOGGER.error("Failed to read kjsgen layout {}", location, e);
            }
        });
        return jsons;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonObject> jsons, ResourceManager resourceManager, ProfilerFiller profiler) {
        int loaded = 0;
        for (Map.Entry<ResourceLocation, JsonObject> entry : jsons.entrySet()) {
            try {
                RecipeTypeDefinition definition = RecipeTypeDefinition.fromJson(entry.getValue());
                RecipeTypeRegistry.register(definition);
                loaded++;
            } catch (Exception e) {
                KjsGen.LOGGER.error("Invalid kjsgen layout {}", entry.getKey(), e);
            }
        }
        if (loaded > 0) {
            KjsGen.LOGGER.info("kjsgen loaded {} JSON recipe layouts", loaded);
        }
    }
}
