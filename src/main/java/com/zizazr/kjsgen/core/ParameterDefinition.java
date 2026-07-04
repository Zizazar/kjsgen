package com.zizazr.kjsgen.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * A configurable recipe parameter (cooking time, xp, etc.) exposed in the
 * editor's parameter panel. Values are stored as strings in {@link RecipeInstance}.
 *
 * @param key          parameter key, also used by codegen handlers
 * @param type         value type
 * @param defaultValue default value as string
 * @param options      allowed values for ENUM type
 */
public record ParameterDefinition(String key, ParamType type, String defaultValue, List<String> options) {
    public enum ParamType {
        STRING, INT, FLOAT, BOOL, ENUM
    }

    public static ParameterDefinition ofInt(String key, int defaultValue) {
        return new ParameterDefinition(key, ParamType.INT, Integer.toString(defaultValue), List.of());
    }

    public static ParameterDefinition ofFloat(String key, float defaultValue) {
        return new ParameterDefinition(key, ParamType.FLOAT, Float.toString(defaultValue), List.of());
    }

    public static ParameterDefinition ofString(String key, String defaultValue) {
        return new ParameterDefinition(key, ParamType.STRING, defaultValue, List.of());
    }

    public static ParameterDefinition ofBool(String key, boolean defaultValue) {
        return new ParameterDefinition(key, ParamType.BOOL, Boolean.toString(defaultValue), List.of());
    }

    public static ParameterDefinition ofEnum(String key, String defaultValue, List<String> options) {
        return new ParameterDefinition(key, ParamType.ENUM, defaultValue, options);
    }

    /** Translation key of this parameter's label. */
    public String translationKey() {
        return "kjsgen.param." + key;
    }

    public boolean isValid(String value) {
        if (value == null) return false;
        try {
            return switch (type) {
                case STRING -> true;
                case INT -> {
                    Integer.parseInt(value.trim());
                    yield true;
                }
                case FLOAT -> {
                    Float.parseFloat(value.trim());
                    yield true;
                }
                case BOOL -> value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
                case ENUM -> options.contains(value);
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("key", key);
        json.addProperty("type", type.name());
        json.addProperty("default", defaultValue);
        if (!options.isEmpty()) {
            JsonArray arr = new JsonArray();
            options.forEach(arr::add);
            json.add("options", arr);
        }
        return json;
    }

    public static ParameterDefinition fromJson(JsonObject json) {
        List<String> options = List.of();
        if (json.has("options")) {
            options = json.get("options").getAsJsonArray().asList().stream()
                    .map(e -> e.getAsString()).toList();
        }
        return new ParameterDefinition(
                json.get("key").getAsString(),
                ParamType.valueOf(json.has("type") ? json.get("type").getAsString() : "STRING"),
                json.has("default") ? json.get("default").getAsString() : "",
                options
        );
    }
}
