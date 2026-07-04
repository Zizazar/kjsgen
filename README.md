# KubeJS Generator (kjsgen)

Инструмент разработчика модпаков для **Minecraft 1.21.1 / NeoForge**: графический редактор рецептов
в стиле JEI с экспортом в готовые **KubeJS**-скрипты (`kubejs/server_scripts/*.js`).

UI построен на [LDLib2](https://github.com/Low-Drag-MC/LDLib2).

## Возможности

- **Редактор рецептов** с раскладкой слотов как в JEI (слоты 18×18, стрелки/пламя из ванильных текстур).
- Встроенные типы: Crafting (Shaped/Shapeless), Smelting, Blasting, Smoking, Campfire Cooking,
  Stonecutting, Smithing (Transform/Trim), Brewing (через MoreJS).
- Из коробки шаблоны для **Create** (pressing, crushing, mixing) и **Mekanism** (enriching, crushing) —
  активны всегда, но экспортируются с обёрткой `if (Platform.isLoaded(...))`.
- **Импорт layout'ов из JEI-плагинов**: кнопка «Импорт из JEI» в окне выбора типа считывает
  категории рецептов всех установленных модов через JEI Runtime API — позиции и роли слотов
  берутся из реального `IRecipeCategory#setRecipe` мода, поэтому раскладка совпадает с JEI
  пиксель в пиксель. Синтаксис KubeJS для таких типов JEI не знает, поэтому импортированный тип
  получает редактируемый параметр «Шаблон» с заготовкой
  `event.recipes.<mod>.<type>([{outputs}], [{inputs}])` — поправьте под вики конкретного мода.
  Импортированные layout'ы сохраняются в `<game dir>/kjsgen/layouts/*.json` (можно править руками)
  и загружаются при старте даже без JEI.
- Слоты: предмет / тег предметов / жидкость / тег жидкости, количество, шанс выпадения, data-компоненты (SNBT).
- Поиск предметов/тегов/жидкостей по id и названию.
- Параметры рецепта: id, group, комментарий, целевой файл экспорта, условие «если мод загружен»,
  время готовки, опыт и т.д.
- **Валидация в реальном времени**: пустые обязательные слоты (красная рамка), некорректные id,
  дублирующиеся id рецептов в проекте.
- **Живой предпросмотр** сгенерированного KubeJS-кода.
- **Проекты**: сохранение/загрузка в `<game dir>/kjsgen/projects/*.json`, независимо от экспорта.
- **Умный экспорт**: блоки-маркеры `// kjsgen:start <проект>` / `// kjsgen:end <проект>` —
  повторный экспорт заменяет только свой блок и не трогает ручной код вне маркеров.
  Рецепты можно раскладывать по нескольким `.js`-файлам (поле «Файл экспорта»).
- Запись строго внутри `kubejs/server_scripts/` текущего инстанса.
- Доступ только в одиночной игре или операторам (уровень 2+).
- Локализация: `en_us`, `ru_ru`.

## Использование

1. Установите **LDLib2** (обязательно) и **KubeJS** (нужен, чтобы экспортированные скрипты заработали;
   сам редактор работает и без него).
2. В игре нажмите **K** (настраивается в управлении) — откроется редактор.
3. «+ Добавить рецепт» → выберите тип из сетки (есть поиск по названию/моду).
4. Кликните по слоту: поиск/ручной ввод id, тег/жидкость, количество/шанс/компоненты.
   ПКМ по слоту — очистить.
5. Справа — параметры рецепта и живой предпросмотр кода.
6. «Экспорт» → проверка, предпросмотр всех файлов, запись в `kubejs/server_scripts/`.
7. `/reload` в игре — KubeJS подхватит новые рецепты.

Рецепты варки зелий экспортируются под аддон [MoreJS](https://kubejs.com/wiki/addons/morejs)
(`MoreJSEvents.registerPotionBrewing`).

## Addon API

Сторонний мод может добавить свои типы рецептов без правок kjsgen — двумя способами.

### 1. Только JSON (без кода)

Положите файл в `assets/<ваш_namespace>/kjsgen_layouts/<имя>.json`:

```json
{
  "id": "mymod:press",
  "mod": "mymod",
  "icon": "mymod:press_machine",
  "width": 82,
  "height": 34,
  "slots": [
    { "key": "input",  "role": "INPUT",  "x": 0,  "y": 8, "required": true, "item": true, "tag": true },
    { "key": "output", "role": "OUTPUT", "x": 60, "y": 8, "required": true, "item": true, "count": true, "chance": true }
  ],
  "decorations": [ { "type": "ARROW", "x": 26, "y": 9 } ],
  "parameters": [
    { "key": "__template", "type": "STRING",
      "default": "event.recipes.mymod.press({slot:output}, {slot:input}).time({param:time})" },
    { "key": "time", "type": "INT", "default": "100" }
  ],
  "codegen": "kjsgen:template",
  "requiresMod": "mymod"
}
```

Плейсхолдеры шаблона: `{slot:KEY}`, `{inputs}`, `{outputs}` (все заполненные входы/выходы через запятую),
`{param:KEY}`, `{param_str:KEY}`, `{id}`, `{group}`. Суффикс `.id(...)`/`.group(...)` добавляется
автоматически (отключается параметром `__suffix_id = "false"`).

Типы декораций: `ARROW`, `FLAME`, `PLUS`, `TEXT` (+ поле `"text"`).

### 2. Java (свой генератор кода)

```java
modEventBus.addListener((RegisterRecipeTypesEvent e) -> {
    e.registerCodegen("mymod:press", (recipe, type) -> "event.recipes.mymod.press(...)");
    e.registerType(new RecipeTypeDefinition("mymod:press", "mymod", "mymod:press_machine",
            82, 34, slots, decorations, parameters, "mymod:press", "mymod"));
});
```

Событие `com.zizazr.kjsgen.api.RegisterRecipeTypesEvent` приходит на mod bus во время common setup.
`RecipeCodegen.wrapperHeader()/wrapperFooter()` позволяют генерировать код в другом событии KubeJS
(как встроенный brewing использует MoreJS).

## Структура проекта

```
core/                 — модель данных (SlotContent, RecipeInstance, RecipeProject, реестр типов, валидатор)
templates/            — встроенные типы + загрузчик JSON-раскладок (kjsgen_layouts)
codegen/              — RecipeCodegen + генераторы KubeJS-кода по типам
integration/kubejs/   — сборка скрипта и экспорт с маркерами
ui/                   — экраны на LDLib2 (workspace, канвас, диалоги)
api/                  — событие регистрации для аддонов
```

## Сборка

```bash
./gradlew build   # jar в build/libs/
```

Зависимости: NeoForge 21.1.x, LDLib2 2.2.x (maven.firstdark.dev/snapshots), Java 21.
