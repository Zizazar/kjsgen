# Adding a new recipe type

Every recipe type is a [`RecipeTypeDefinition`](../src/main/java/com/zizazr/kjsgen/core/RecipeTypeDefinition.java):
a layout (canvas size, slots, arrows/flames/labels) plus a reference to a
codegen handler that turns a filled-in instance into a KubeJS statement. There
are two ways to add one — pick the JSON route unless the target syntax needs
logic a template placeholder can't express.

## Option A: JSON layout + template codegen (no Java, preferred)

Used for most addon recipe types (Create, Mekanism). Drop a file in
`src/main/resources/assets/kjsgen/kjsgen_layouts/<name>.json` — it's picked up
automatically by [JsonLayoutLoader](../src/main/java/com/zizazr/kjsgen/templates/JsonLayoutLoader.java)
on resource reload, no registration code needed.

```json
{
  "id": "kjsgen:mekanism_sawing",
  "mod": "mekanism",
  "icon": "mekanism:precision_sawmill",
  "width": 82,
  "height": 36,
  "slots": [
    { "key": "input", "role": "INPUT", "x": 0, "y": 9, "required": true, "item": true, "tag": true },
    { "key": "output", "role": "OUTPUT", "x": 60, "y": 0, "required": true, "item": true, "count": true },
    { "key": "extraOutput", "role": "OUTPUT", "x": 60, "y": 18, "required": false, "item": true, "count": true }
  ],
  "decorations": [
    { "type": "ARROW", "x": 26, "y": 9 }
  ],
  "parameters": [
    { "key": "secondaryChance", "type": "FLOAT", "default": "1.0" },
    { "key": "__template", "type": "STRING", "default": "event.recipes.mekanism.sawing({slot:output}{opt_slot_chance:extraOutput:secondaryChance}, {slot:input})" }
  ],
  "codegen": "kjsgen:template",
  "requiresMod": "mekanism"
}
```

Field notes (see [SlotDefinition](../src/main/java/com/zizazr/kjsgen/core/SlotDefinition.java),
[ParameterDefinition](../src/main/java/com/zizazr/kjsgen/core/ParameterDefinition.java),
[LayoutDecoration](../src/main/java/com/zizazr/kjsgen/core/LayoutDecoration.java) for full field lists):

- `width`/`height` and slot `x`/`y` are JEI-style pixel coordinates; slots are 18x18px.
  Match the real JEI category layout for that machine when one exists.
- `role` is `INPUT`, `OUTPUT`, or `CATALYST`. Slot flags (`item`, `tag`, `fluid`, `count`,
  `chance`, `list`) control what content the slot accepts and what the editor lets you edit.
  A `"list": true` slot renders as one slot + a '+' button for a variable-length ingredient list.
- `decorations` types: `ARROW`, `FLAME`, `PLUS`, `TEXT` (needs a `"text"` field).
- `codegen` must be `"kjsgen:template"` to use the placeholder engine below. `requiresMod` hides
  the type from the picker (and skips it on export) when that mod isn't loaded; use `"minecraft"`
  or `"morejs"` for types that should always be visible.
- `__template` is a hidden parameter (starts with `__`, never shown in the editor) holding the
  KubeJS statement with placeholders, resolved by
  [TemplateRecipeCodegen](../src/main/java/com/zizazr/kjsgen/codegen/handlers/TemplateRecipeCodegen.java):
  - `{slot:KEY}` — ingredient expression for an input/catalyst slot, result expression for an output slot
  - `{opt_slot:KEY}` — like `{slot:KEY}` but expands to `", EXPR"` when filled, `""` when empty
    (for a trailing optional argument)
  - `{opt_slot_chance:SLOT:PARAM}` — optional output + its chance parameter as two adjacent args,
    `", OUTPUT, PARAM"` when filled, `""` when empty
  - `{param:KEY}` / `{param_str:KEY}` — raw / quoted-string parameter value
  - `{id}` / `{group}` — quoted recipe id / group
  - `{inputs}` / `{outputs}` — comma-joined list of all *filled* slots of that role (for a variable
    number of results, e.g. Create crushing byproducts)
  - Add a hidden `__suffix_id` parameter set to `"false"` if the target syntax doesn't support a
    trailing `.id(...)` call (it's appended automatically otherwise).

Then add the display name to both lang files, key `kjsgen.recipe_type.<id with ':' → '.'>`:

```json
"kjsgen.recipe_type.kjsgen.mekanism_sawing": "Mekanism: Sawing"
```
(`src/main/resources/assets/kjsgen/lang/en_us.json` and `ru_ru.json`)

Rebuild/reload resources (or restart `runClient`) and the type appears in the type picker.

## Option B: Java-registered type + custom codegen handler

Needed when the generated statement can't be expressed as one template string (conditional
logic, non-trivial argument ordering, computed values). Follow the built-in vanilla types as a
model:

1. Add a `RecipeTypeDefinition` factory method in
   [BuiltinRecipeTypes](../src/main/java/com/zizazr/kjsgen/templates/BuiltinRecipeTypes.java) (or
   an addon's equivalent registration point) and call it from `register()`.
2. Write a `RecipeCodegen` implementation in `codegen/handlers/`, e.g.
   [StonecuttingRecipeCodegen](../src/main/java/com/zizazr/kjsgen/codegen/handlers/StonecuttingRecipeCodegen.java),
   returning the JS statement for one `RecipeInstance`. Override `wrapperHeader()`/`wrapperFooter()`
   only if the recipe needs a different KubeJS event wrapper (see
   [BrewingRecipeCodegen](../src/main/java/com/zizazr/kjsgen/codegen/handlers/BrewingRecipeCodegen.java)
   for a MoreJS example).
3. Register the handler under the type's `codegenId` in
   [CodegenRegistry.registerBuiltins()](../src/main/java/com/zizazr/kjsgen/codegen/CodegenRegistry.java).
4. Add the lang entries as in Option A.

## Verifying

- `./gradlew compileJava` to check API usage.
- `./gradlew runClient`, open the vanilla editor (key **J**), confirm the new type appears in the
  type picker, its layout renders correctly, and exporting produces the expected KubeJS statement
  in `kubejs/server_scripts/`.
