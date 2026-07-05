package com.zizazr.kjsgen.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A working session: a named set of {@link RecipeInstance}s plus export options.
 * Persisted as JSON independently from the KubeJS export.
 */
public final class RecipeProject {
    public static final int FORMAT_VERSION = 1;

    private String name;
    /** Default export file name (without .js extension). */
    private String defaultTargetFile = "kjsgen_recipes";
    /** Emit a human readable comment above each generated recipe. */
    private boolean exportComments = true;
    /** Run the vanilla "/reload" command right after a successful export. */
    private boolean reloadOnExport = false;
    private final List<RecipeInstance> recipes = new ArrayList<>();

    public RecipeProject(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String defaultTargetFile() {
        return defaultTargetFile;
    }

    public void setDefaultTargetFile(String defaultTargetFile) {
        if (defaultTargetFile != null && !defaultTargetFile.isBlank()) {
            this.defaultTargetFile = defaultTargetFile.trim();
        }
    }

    public boolean exportComments() {
        return exportComments;
    }

    public void setExportComments(boolean exportComments) {
        this.exportComments = exportComments;
    }

    public boolean reloadOnExport() {
        return reloadOnExport;
    }

    public void setReloadOnExport(boolean reloadOnExport) {
        this.reloadOnExport = reloadOnExport;
    }

    public List<RecipeInstance> recipes() {
        return recipes;
    }

    public Optional<RecipeInstance> recipeByUid(String uid) {
        return recipes.stream().filter(r -> r.uid().equals(uid)).findFirst();
    }

    public void add(RecipeInstance recipe) {
        recipes.add(recipe);
    }

    public void remove(RecipeInstance recipe) {
        recipes.removeIf(r -> r.uid().equals(recipe.uid()));
    }

    /** Effective export file of a recipe (recipe override or project default). */
    public String targetFileOf(RecipeInstance recipe) {
        return recipe.targetFile().isEmpty() ? defaultTargetFile : recipe.targetFile();
    }

    /** Recipes grouped by their effective export file, insertion-ordered. */
    public Map<String, List<RecipeInstance>> recipesByTargetFile() {
        Map<String, List<RecipeInstance>> byFile = new LinkedHashMap<>();
        for (RecipeInstance recipe : recipes) {
            byFile.computeIfAbsent(targetFileOf(recipe), f -> new ArrayList<>()).add(recipe);
        }
        return byFile;
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("format", FORMAT_VERSION);
        json.addProperty("name", name);
        json.addProperty("defaultTargetFile", defaultTargetFile);
        json.addProperty("exportComments", exportComments);
        json.addProperty("reloadOnExport", reloadOnExport);
        JsonArray arr = new JsonArray();
        recipes.forEach(r -> arr.add(r.toJson()));
        json.add("recipes", arr);
        return json;
    }

    public static RecipeProject fromJson(JsonObject json) {
        RecipeProject project = new RecipeProject(json.has("name") ? json.get("name").getAsString() : "unnamed");
        if (json.has("defaultTargetFile")) project.setDefaultTargetFile(json.get("defaultTargetFile").getAsString());
        if (json.has("exportComments")) project.exportComments = json.get("exportComments").getAsBoolean();
        if (json.has("reloadOnExport")) project.reloadOnExport = json.get("reloadOnExport").getAsBoolean();
        if (json.has("recipes")) {
            json.getAsJsonArray("recipes").forEach(e -> project.recipes.add(RecipeInstance.fromJson(e.getAsJsonObject())));
        }
        return project;
    }
}
