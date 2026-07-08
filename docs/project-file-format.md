# Формат JSON-файлов проектов

Проект kjsgen — это рабочая сессия визуального редактора: именованный набор
рецептов плюс настройки экспорта. Каждый проект сохраняется отдельным JSON-файлом
и **не** является итоговым KubeJS-скриптом (тот генерируется из проекта при
экспорте в `kubejs/server_scripts/`).

## Расположение файлов

Проекты лежат в `<папка игры>/kjsgen/projects/<имя>.json`
(см. [ProjectManager](../src/main/java/com/zizazr/kjsgen/core/ProjectManager.java)).
Имя файла — это `sanitizeName()` от названия проекта: все символы кроме
`A-Z a-z 0-9 _ -` заменяются на `_`. Файлы пишутся в UTF-8, pretty-printed.
Проект по умолчанию называется `default`.

Сериализацию задают `toJson()` / `fromJson()` в
[RecipeProject](../src/main/java/com/zizazr/kjsgen/core/RecipeProject.java),
[RecipeInstance](../src/main/java/com/zizazr/kjsgen/core/RecipeInstance.java) и
[SlotContent](../src/main/java/com/zizazr/kjsgen/core/SlotContent.java) — этот
документ описывает именно их.

## Верхний уровень (`RecipeProject`)

```json
{
  "format": 1,
  "name": "my_pack",
  "defaultTargetFile": "kjsgen_recipes",
  "exportComments": true,
  "reloadOnExport": false,
  "recipes": [ /* массив объектов RecipeInstance, см. ниже */ ]
}
```

| Поле                | Тип     | По умолчанию       | Назначение |
|---------------------|---------|--------------------|------------|
| `format`            | int     | `1`                | Версия формата файла (`RecipeProject.FORMAT_VERSION`). Пишется всегда. |
| `name`              | string  | `"unnamed"`        | Название проекта. Если отсутствует при чтении — `"unnamed"`. |
| `defaultTargetFile` | string  | `"kjsgen_recipes"` | Имя файла экспорта по умолчанию (без `.js`). Используется для рецептов, у которых не задан свой `targetFile`. |
| `exportComments`    | boolean | `true`             | Выводить ли человекочитаемый комментарий над каждым сгенерированным рецептом. |
| `reloadOnExport`    | boolean | `false`            | Выполнять ли `/reload` сразу после успешного экспорта. |
| `recipes`           | array   | `[]`               | Список рецептов проекта. |

Все поля, кроме `recipes`, при чтении необязательны — отсутствующее поле берёт
значение по умолчанию из колонки выше.

## Рецепт (`RecipeInstance`)

Один элемент массива `recipes`:

```json
{
  "uid": "3f2b0c9a-8d1e-4c77-9b0a-1e2d3c4b5a60",
  "type": "kjsgen:shaped",
  "recipeId": "my_pack:reinforced_plank",
  "group": "planks",
  "comment": "Усиленная доска",
  "targetFile": "building_blocks",
  "ifModLoaded": "create",
  "replaceRecipe": true,
  "slots": {
    "in0": { "kind": "ITEM", "id": "minecraft:oak_planks" },
    "in1": { "kind": "ITEM_TAG", "id": "forge:ingots/iron" },
    "output": { "kind": "ITEM", "id": "my_pack:reinforced_plank", "count": 4 }
  },
  "params": {
    "xp": "0.5",
    "cookingTime": "120"
  }
}
```

| Поле            | Тип     | По умолч. | Записывается когда | Назначение |
|-----------------|---------|-----------|--------------------|------------|
| `uid`           | string  | новый UUID | всегда            | Стабильный идентификатор внутри проекта; переживает переименование `recipeId`. При чтении генерируется, если отсутствует. |
| `type`          | string  | —         | всегда             | Id типа рецепта (`kjsgen:shaped`, `kjsgen:create_pressing`, …). Ссылается на `RecipeTypeDefinition` в реестре. **Обязательное поле.** |
| `recipeId`      | string  | `""`      | если не пустое      | Явный id рецепта (`"mypack:my_recipe"`). Пусто = KubeJS сгенерирует id сам. |
| `group`         | string  | `""`      | если не пустое      | Группа рецепта (объединение в JEI/книге рецептов). |
| `comment`       | string  | `""`      | если не пустое      | Комментарий, выводимый над сгенерированной строкой. |
| `targetFile`    | string  | `""`      | если не пустое      | Файл экспорта (без `.js`) для этого рецепта. Пусто = `defaultTargetFile` проекта. |
| `ifModLoaded`   | string  | `""`      | если не пустое      | Id мода: рецепт при экспорте оборачивается в `Platform.isLoaded(...)`. В коде поле называется `conditionModLoaded`. |
| `replaceRecipe` | boolean | `true`    | только если `false` | Выводить ли `event.remove(...)` перед рецептом, чтобы повторный экспорт заменял, а не дублировал. Так как значение по умолчанию `true`, оно пишется в JSON **только** когда явно выключено. |
| `slots`         | object  | `{}`      | всегда             | Карта «ключ слота → содержимое». См. ниже. |
| `params`        | object  | `{}`      | всегда             | Карта «ключ параметра → строковое значение». См. ниже. |

### `slots` — содержимое слотов (`SlotContent`)

Ключи объекта — это `key` из `SlotDefinition` соответствующего типа рецепта
(`input`, `output`, `in0`…`in8`, `template`, `base`, `addition` и т. п.).

Слоты-списки (флаг `list` в определении типа, например список ингредиентов
shapeless-рецепта или побочные выходы) хранятся как отдельные пронумерованные
ключи `<база>0`, `<база>1`, … — например `in0`, `in1`, `in2`. Нумерация всегда
непрерывная: при удалении записи оставшиеся уплотняются
(см. `RecipeInstance.setListSlot`). Пустой слот в JSON вообще не сохраняется.

Значение каждого слота:

```json
{ "kind": "ITEM", "id": "minecraft:stick", "count": 2, "amount": 1000, "chance": 0.5, "components": "{Damage:5}" }
```

| Поле         | Тип    | По умолч. | Записывается когда | Назначение |
|--------------|--------|-----------|--------------------|------------|
| `kind`       | string | `EMPTY`   | всегда             | Тип содержимого — значение enum `ContentKind` (см. таблицу ниже). |
| `id`         | string | `""`      | всегда             | Registry-id предмета/жидкости/химиката (`minecraft:stick`), либо id тега **без** `#` (`minecraft:planks`). |
| `count`      | int    | `1`       | если `≠ 1`         | Размер стака для предметов. Минимум 1. |
| `amount`     | int    | `1000`    | если `≠ 1000`      | Объём в mB для жидкостей/химикатов. Минимум 1. |
| `chance`     | float  | `1.0`     | если `< 1.0`       | Шанс выпадения выхода (`1.0` = гарантированно). Осмыслен только для выходов типов, которые это поддерживают. Ограничен диапазоном `0.0–1.0`. |
| `components` | string | `""`      | если не пустое      | Компоненты данных / NBT в нотации SNBT, например `{custom_name:'\"Foo\"'}` или `[minecraft:damage=5]`. |

Поля с значением по умолчанию опускаются при записи — минимальный предмет это
просто `{ "kind": "ITEM", "id": "minecraft:stick" }`.

#### Значения `kind` (`ContentKind`)

| `kind`         | Что это | Единица | Принимает `#` теги |
|----------------|---------|---------|--------------------|
| `EMPTY`        | пусто / wildcard | — | — |
| `ITEM`         | конкретный предмет | `count` | нет |
| `ITEM_TAG`     | тег предметов (`#minecraft:planks`) | — | да |
| `FLUID`        | конкретная жидкость | `amount` mB | нет |
| `FLUID_TAG`    | тег жидкостей | `amount` mB | да |
| `CHEMICAL`     | химикат Mekanism (gas/infuse type/pigment/slurry) | `amount` mB | нет |
| `CHEMICAL_TAG` | тег химикатов Mekanism | `amount` mB | да |

Какие `kind` разрешены в конкретном слоте, задаётся флагами `SlotDefinition`
(`item`/`tag`/`fluid`/`chemical`) типа рецепта, а не файлом проекта.

### `params` — значения параметров

Плоская карта «строка → строка». Ключи — это `key` из `ParameterDefinition`
типа рецепта (например `xp`, `cookingTime`, `processingTime`, `heat`). Значения
**всегда строки**, даже для числовых/булевых параметров — при кодогенерации они
парсятся через `paramInt` / `paramFloat` / `paramBool`.

Хранятся только явно заданные значения; если значение совпадает с `default`
параметра, оно всё равно может присутствовать — недостающие параметры при чтении
берут `default` из определения типа. Служебные параметры с ключом на `__`
(например `__template`) относятся к **определению типа**, а не к экземпляру
рецепта, и в файле проекта не встречаются.

---

# Маппинг типов kjsgen → типы рецептов Minecraft

Поле `type` рецепта ссылается на `RecipeTypeDefinition`. Встроенные ванильные
типы регистрируются в
[BuiltinRecipeTypes](../src/main/java/com/zizazr/kjsgen/templates/BuiltinRecipeTypes.java);
каждому соответствует свой кодогенератор в
[codegen/handlers/](../src/main/java/com/zizazr/kjsgen/codegen/handlers), который
выдаёт KubeJS-вызов и (для удаления/замены) id ванильного типа рецепта.

## Ванильные типы (всегда доступны)

| Тип kjsgen (`type`)        | Тип рецепта Minecraft        | KubeJS-вызов при экспорте        | Примечание |
|----------------------------|------------------------------|----------------------------------|------------|
| `kjsgen:shaped`            | `minecraft:crafting_shaped`  | `event.shaped(...)`              | Сетка 3×3, слоты `in0`…`in8` + `output`. |
| `kjsgen:shapeless`         | `minecraft:crafting_shapeless` | `event.shapeless(...)`         | Динамический список ингредиентов `in0`,`in1`,… + `output`. |
| `kjsgen:smelting`          | `minecraft:smelting`         | `event.smelting(...)`            | xp 0.1, время 200 тиков по умолчанию. |
| `kjsgen:blasting`          | `minecraft:blasting`         | `event.blasting(...)`            | xp 0.1, время 100. |
| `kjsgen:smoking`           | `minecraft:smoking`          | `event.smoking(...)`             | xp 0.1, время 100. |
| `kjsgen:campfire_cooking`  | `minecraft:campfire_cooking` | `event.campfireCooking(...)`     | xp 0.0, время 600. |
| `kjsgen:stonecutting`      | `minecraft:stonecutting`     | `event.stonecutting(...)`        | Резак по камню. |
| `kjsgen:smithing_transform`| `minecraft:smithing_transform` | `event.smithingTransform(...)` | Слоты `template`/`base`/`addition`/`output`. |
| `kjsgen:smithing_trim`     | `minecraft:smithing_trim`    | `event.smithingTrim(...)`        | Без слота `output` (отделка брони). |
| `kjsgen:brewing`           | *варка (не датапак-рецепт)*  | `event.addCustomBrewing(...)`    | Требует аддон **MoreJS**; оборачивается в `MoreJSEvents.registerPotionBrewing`. |

Параметры кулинарных типов: `xp` (FLOAT), `cookingTime` (INT). Значения выше —
значения по умолчанию каждого варианта.

## Типы модов (видны только если мод загружен)

Эти типы задаются JSON-лейаутами в
[`assets/kjsgen/kjsgen_layouts/`](../src/main/resources/assets/kjsgen/kjsgen_layouts)
через шаблонный кодогенератор `kjsgen:template`. Столбец «JEI-категория» — это
поле `jei` лейаута; по нему кнопка «Edit in kjsgen» сопоставляет показанный в JEI
рецепт с этим типом. `requiresMod` скрывает тип из списка (и пропускает при
экспорте), если мод не установлен.

### Create (`requiresMod: create`)

| Тип kjsgen (`type`)               | JEI-категория (`jei`)        | KubeJS-вызов |
|-----------------------------------|------------------------------|--------------|
| `kjsgen:create_crushing`          | `create:crushing`            | `event.recipes.create.crushing(...)` |
| `kjsgen:create_milling`           | `create:milling`             | `event.recipes.create.milling(...)` |
| `kjsgen:create_cutting`           | `create:sawing`              | `event.recipes.create.cutting(...)` |
| `kjsgen:create_pressing`          | `create:pressing`            | `event.recipes.create.pressing(...)` |
| `kjsgen:create_mixing`            | `create:mixing`              | `event.recipes.create.mixing(...)` |
| `kjsgen:create_compacting`        | `create:packing`             | `event.recipes.create.compacting(...)` |
| `kjsgen:create_filling`           | `create:spout_filling`       | `event.recipes.create.filling(...)` |
| `kjsgen:create_emptying`          | `create:draining`            | `event.recipes.create.emptying(...)` |
| `kjsgen:create_deploying`         | `create:deploying`           | `event.recipes.create.deploying(...)` |
| `kjsgen:create_haunting`          | `create:fan_haunting`        | `event.recipes.create.haunting(...)` |
| `kjsgen:create_splashing`         | `create:fan_washing`         | `event.recipes.create.splashing(...)` |
| `kjsgen:create_sandpaper_polishing` | `create:sandpaper_polishing` | `event.recipes.create.sandpaper_polishing(...)` |
| `kjsgen:create_mechanical_crafting` | `create:mechanical_crafting` | спец-редактор «grid» (изменяемая сетка W×H), [CreateMechanicalCraftingCodegen](../src/main/java/com/zizazr/kjsgen/codegen/handlers/CreateMechanicalCraftingCodegen.java) |
| `kjsgen:create_sequenced_assembly` | `create:sequenced_assembly` | спец-редактор «sequence» (список стадий), [CreateSequencedAssemblyCodegen](../src/main/java/com/zizazr/kjsgen/codegen/handlers/CreateSequencedAssemblyCodegen.java) |

### Mekanism (`requiresMod: mekanism`)

| Тип kjsgen (`type`)                  | JEI-категория (`jei`)          | KubeJS-вызов |
|--------------------------------------|--------------------------------|--------------|
| `kjsgen:mekanism_enriching`          | `mekanism:enriching`           | `event.recipes.mekanism.enriching(...)` |
| `kjsgen:mekanism_crushing`           | `mekanism:crushing`            | `event.recipes.mekanism.crushing(...)` |
| `kjsgen:mekanism_smelting`           | `mekanism:smelting`            | `event.recipes.mekanism.smelting(...)` |
| `kjsgen:mekanism_combining`          | `mekanism:combining`           | `event.recipes.mekanism.combining(...)` |
| `kjsgen:mekanism_sawing`             | `mekanism:sawing`              | `event.recipes.mekanism.sawing(...)` |
| `kjsgen:mekanism_crystallizing`      | `mekanism:crystallizing`       | `event.recipes.mekanism.crystallizing(...)` |
| `kjsgen:mekanism_oxidizing`          | `mekanism:oxidizing`           | `event.recipes.mekanism.oxidizing(...)` |
| `kjsgen:mekanism_metallurgic_infusing` | `mekanism:metallurgic_infusing` | `event.recipes.mekanism.metallurgic_infusing(...)` |
| `kjsgen:mekanism_energy_conversion`  | `mekanism:energy_conversion`   | `event.recipes.mekanism.energy_conversion(...)` |

> Точное сопоставление JEI-категорий проверено и зафиксировано; список типов
> модов может расширяться — актуальный источник истины это файлы в
> `assets/kjsgen/kjsgen_layouts/` (плюс типы, добавленные аддонами через
> `RegisterRecipeTypesEvent`). Полный формат самих лейаутов и правила
> шаблонного кодогенератора описаны в
> [adding-recipe-types.md](adding-recipe-types.md).
