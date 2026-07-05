package com.zizazr.kjsgen.ui;

import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextArea;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.core.ParameterDefinition;
import com.zizazr.kjsgen.core.ProjectManager;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeProject;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import com.zizazr.kjsgen.core.RecipeValidator;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.core.SlotDefinition;
import com.zizazr.kjsgen.core.LayoutDecoration;
import com.zizazr.kjsgen.integration.kubejs.ScriptAssembler;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * The recipe editor workspace: recipe list on the left, JEI-like canvas in the
 * middle, parameters + live code preview on the right.
 */
public class KjsGenUI {
    private RecipeProject project = ProjectManager.current();
    private String selectedUid;

    private UIElement root;
    private ScrollerView recipeList;
    private Label listHeader;
    private Label typeLabel;
    private UIElement canvasHolder;
    private UIElement validationBox;
    private ScrollerView paramsPanel;
    private TextArea previewArea;
    private TextField projectNameField;

    // cached live JEI canvas for the currently selected recipe (see tryAddJeiCanvas)
    private UIElement jeiCanvas;
    private String jeiCanvasUid;

    public UI buildUI() {
        if (!project.recipes().isEmpty()) {
            selectedUid = project.recipes().get(0).uid();
        }
        root = new UIElement();
        root.layout(layout -> layout
                .width(480)
                .height(250)
                .flexDirection(FlexDirection.COLUMN)
                .paddingAll(5)
                .gapAll(4));
        root.addClass("panel_bg");

        root.addChild(buildTopBar());
        root.addChild(buildMainRow());

        refreshAll();
        return UI.of(root, List.of(StylesheetManager.INSTANCE.getStylesheetSafe(StylesheetManager.MC)));
    }

    public RecipeProject project() {
        return project;
    }

    public UIElement root() {
        return root;
    }

    public Optional<RecipeInstance> selectedRecipe() {
        return selectedUid == null ? Optional.empty() : project.recipeByUid(selectedUid);
    }

    // ------------------------------------------------------------------ top bar

    private UIElement buildTopBar() {
        UIElement bar = new UIElement();
        bar.layout(layout -> layout
                .flexDirection(FlexDirection.ROW)
                .widthPercent(100)
                .alignItems(AlignItems.CENTER)
                .gapAll(3));

        Label title = new Label();
        title.setText("kjsgen.title");

        UIElement spacer = new UIElement();
        spacer.layout(layout -> layout.flex(1));

        projectNameField = new TextField();
        projectNameField.setText(project.name());
        projectNameField.layout(layout -> layout.width(80).height(14));
        projectNameField.setTextResponder(name -> {
            if (!name.isBlank()) {
                project.setName(name.trim());
            }
        });
        projectNameField.style(style -> style.tooltips(Component.translatable("kjsgen.ui.project_name")));

        bar.addChildren(
                title,
                spacer,
                projectNameField,
                new Button().setText("kjsgen.ui.save").setOnClick(e -> saveProject(true)),
                new Button().setText("kjsgen.ui.projects").setOnClick(e -> ProjectsDialog.show(this)),
                new Button().setText("kjsgen.ui.export").setOnClick(e -> ExportDialog.show(this))
        );
        return bar;
    }

    void saveProject(boolean notify) {
        try {
            ProjectManager.save(project);
            if (notify) {
                Dialog.showNotification(Component.translatable("kjsgen.ui.saved").getString(), 1.5f).show(root);
            }
        } catch (IOException e) {
            KjsGen.LOGGER.error("Failed to save project", e);
            Dialog.showNotification(Component.translatable("kjsgen.error.save_failed", e.getMessage()).getString(), 4f).show(root);
        }
    }

    /** Switch to another project (from the projects dialog). */
    void setProject(RecipeProject newProject) {
        this.project = newProject;
        ProjectManager.setCurrent(newProject);
        this.jeiCanvas = null;
        this.jeiCanvasUid = null;
        this.selectedUid = newProject.recipes().isEmpty() ? null : newProject.recipes().get(0).uid();
        projectNameField.setText(newProject.name(), false);
        refreshAll();
    }

    // ------------------------------------------------------------------ layout skeleton

    private UIElement buildMainRow() {
        UIElement row = new UIElement();
        row.layout(layout -> layout
                .flexDirection(FlexDirection.ROW)
                .widthPercent(100)
                .flex(1)
                .gapAll(4));

        row.addChild(buildLeftPanel());
        row.addChild(buildCenterPanel());
        row.addChild(buildRightPanel());
        return row;
    }

    private UIElement buildLeftPanel() {
        UIElement panel = new UIElement();
        panel.layout(layout -> layout
                .width(112)
                .heightPercent(100)
                .flexDirection(FlexDirection.COLUMN)
                .gapAll(2));

        listHeader = new Label();
        listHeader.setText("kjsgen.ui.recipes");

        recipeList = new ScrollerView();
        recipeList.layout(layout -> layout.widthPercent(100).flex(1));
        recipeList.viewContainer(view -> view.getLayout().gapAll(1));

        Button addButton = new Button();
        addButton.setText("kjsgen.ui.add_recipe");
        addButton.layout(layout -> layout.widthPercent(100));
        addButton.setOnClick(e -> TypePickerDialog.show(this, type -> {
            RecipeInstance recipe = new RecipeInstance(type.id());
            project.add(recipe);
            selectedUid = recipe.uid();
            refreshAll();
        }));

        panel.addChildren(listHeader, recipeList, addButton);
        return panel;
    }

    private UIElement buildCenterPanel() {
        UIElement panel = new UIElement();
        panel.layout(layout -> layout
                .flex(1)
                .heightPercent(100)
                .flexDirection(FlexDirection.COLUMN)
                .gapAll(2));

        typeLabel = new Label();
        typeLabel.setText("");

        UIElement typeRow = new UIElement();
        typeRow.layout(layout -> layout
                .flexDirection(FlexDirection.ROW)
                .widthPercent(100)
                .alignItems(AlignItems.CENTER)
                .gapAll(3));
        UIElement typeSpacer = new UIElement();
        typeSpacer.layout(layout -> layout.flex(1));
        Button layoutButton = new Button();
        layoutButton.setText("kjsgen.ui.edit_layout");
        layoutButton.setOnClick(e -> selectedRecipe()
                .flatMap(recipe -> RecipeTypeRegistry.get(recipe.typeId()))
                .ifPresent(type -> LayoutEditorDialog.show(this, type)));
        typeRow.addChildren(typeLabel, typeSpacer, layoutButton);

        canvasHolder = new UIElement();
        canvasHolder.layout(layout -> layout
                .widthPercent(100)
                .flex(1)
                .alignItems(AlignItems.CENTER)
                .justifyContent(AlignContent.CENTER));
        canvasHolder.addClass("panel_bg");

        validationBox = new UIElement();
        validationBox.layout(layout -> layout
                .widthPercent(100)
                .flexDirection(FlexDirection.COLUMN)
                .gapAll(1));

        panel.addChildren(typeRow, canvasHolder, validationBox);
        return panel;
    }

    private UIElement buildRightPanel() {
        UIElement panel = new UIElement();
        panel.layout(layout -> layout
                .width(140)
                .heightPercent(100)
                .flexDirection(FlexDirection.COLUMN)
                .gapAll(2));

        paramsPanel = new ScrollerView();
        paramsPanel.layout(layout -> layout.widthPercent(100).flex(1));
        paramsPanel.viewContainer(view -> view.getLayout().gapAll(2));

        Label previewLabel = new Label();
        previewLabel.setText("kjsgen.ui.preview");

        previewArea = new TextArea();
        previewArea.setLines(List.of(""));
        previewArea.layout(layout -> layout.widthPercent(100).height(64));
        previewArea.textAreaStyle(style -> style.fontSize(7));

        panel.addChildren(paramsPanel, previewLabel, previewArea);
        return panel;
    }

    // ------------------------------------------------------------------ refresh logic

    /** Full refresh: list, canvas, params, preview, validation. */
    void refreshAll() {
        refreshList();
        rebuildParams();
        refreshDerived();
    }

    /** Refresh everything that depends on the model but keeps text focus (canvas, preview, validation). */
    void refreshDerived() {
        rebuildCanvas();
        refreshPreview();
    }

    void refreshList() {
        listHeader.setText(Component.translatable("kjsgen.ui.recipes")
                .getString() + " (" + project.recipes().size() + ")", false);
        recipeList.viewContainer(UIElement::clearAllChildren);
        for (RecipeInstance recipe : project.recipes()) {
            recipeList.addScrollViewChild(buildRecipeRow(recipe));
        }
    }

    private UIElement buildRecipeRow(RecipeInstance recipe) {
        boolean selected = recipe.uid().equals(selectedUid);
        RecipeTypeDefinition type = RecipeTypeRegistry.get(recipe.typeId()).orElse(null);

        UIElement row = new UIElement();
        row.layout(layout -> layout
                .flexDirection(FlexDirection.ROW)
                .widthPercent(100)
                .gapAll(1));
        if (selected) {
            row.style(style -> style.backgroundTexture(ColorPattern.WHITE.borderTexture(1)));
        }

        Button select = new Button();
        select.setText(recipe.describe(), false);
        select.layout(layout -> layout.flex(1));
        Component typeName = type != null ? UiTexts.typeName(type) : Component.literal(recipe.typeId());
        select.style(style -> style.tooltips(
                typeName,
                Component.literal(recipe.recipeId().isEmpty() ? "(auto id)" : recipe.recipeId())));
        select.setOnClick(e -> {
            selectedUid = recipe.uid();
            refreshList();
            rebuildParams();
            refreshDerived();
        });

        Button duplicate = new Button();
        duplicate.noText();
        duplicate.layout(layout -> layout.width(14).height(14));
        duplicate.addPreIcon(com.lowdragmc.lowdraglib2.gui.texture.Icons.COPY);
        duplicate.style(style -> style.tooltips(Component.translatable("kjsgen.ui.duplicate")));
        duplicate.setOnClick(e -> {
            RecipeInstance copy = recipe.copy();
            project.add(copy);
            selectedUid = copy.uid();
            refreshAll();
        });

        Button delete = new Button();
        delete.noText();
        delete.layout(layout -> layout.width(14).height(14));
        delete.addPreIcon(com.lowdragmc.lowdraglib2.gui.texture.Icons.REMOVE);
        delete.style(style -> style.tooltips(Component.translatable("kjsgen.ui.delete")));
        delete.setOnClick(e -> {
            project.remove(recipe);
            if (recipe.uid().equals(selectedUid)) {
                selectedUid = project.recipes().isEmpty() ? null : project.recipes().get(0).uid();
            }
            refreshAll();
        });

        row.addChildren(select, duplicate, delete);
        return row;
    }

    // ------------------------------------------------------------------ canvas

    void rebuildCanvas() {
        canvasHolder.clearAllChildren();
        validationBox.clearAllChildren();

        RecipeInstance recipe = selectedRecipe().orElse(null);
        if (recipe == null) {
            typeLabel.setText("kjsgen.ui.no_recipe");
            canvasHolder.addChild(new Label().setText("kjsgen.ui.no_recipe_hint"));
            return;
        }
        RecipeTypeDefinition type = RecipeTypeRegistry.get(recipe.typeId()).orElse(null);
        if (type == null) {
            typeLabel.setText("kjsgen.ui.unknown_type");
            canvasHolder.addChild(new Label().setText(recipe.typeId(), false));
            return;
        }
        typeLabel.setText(UiTexts.typeName(type));

        List<RecipeValidator.Issue> issues = RecipeValidator.validate(recipe, type);
        issues.addAll(RecipeValidator.validateProject(project));

        // Live JEI render: for types imported from a JEI category, draw the mod's real
        // panel (machine, arrows, custom text) with the user's items in the slots.
        // Falls back to the static slot canvas below when JEI/world/sample isn't available.
        if (tryAddJeiCanvas(recipe, type, issues)) {
            addValidationMessages(issues);
            return;
        }

        UIElement canvas = new UIElement();
        canvas.layout(layout -> layout.width(type.canvasWidth()).height(type.canvasHeight()));

        for (LayoutDecoration decoration : type.decorations()) {
            canvas.addChild(buildDecoration(decoration));
        }
        for (SlotDefinition slotDef : type.slots()) {
            canvas.addChild(buildCanvasSlot(recipe, type, slotDef, issues));
        }
        canvasHolder.addChild(canvas);
        addValidationMessages(issues);
    }

    /**
     * Adds a live JEI-rendered canvas for the selected recipe when possible.
     * The built element is cached per recipe uid so text/param edits (which
     * trigger frequent canvas refreshes) don't rebuild the JEI layout each time.
     *
     * @return true when a live canvas was added (skip the static canvas)
     */
    /** Drops the cached live JEI canvas (e.g. after the type's layout was customized). */
    void invalidateJeiCanvas() {
        jeiCanvas = null;
        jeiCanvasUid = null;
    }

    private boolean tryAddJeiCanvas(RecipeInstance recipe, RecipeTypeDefinition type,
                                    List<RecipeValidator.Issue> issues) {
        if (!com.zizazr.kjsgen.KjsGen.isJeiLoaded()
                || !com.zizazr.kjsgen.integration.jei.JeiCanvasFacade.runtimeAvailable()) {
            return false;
        }
        if (jeiCanvas == null || !recipe.uid().equals(jeiCanvasUid)) {
            jeiCanvas = com.zizazr.kjsgen.integration.jei.JeiCanvasFacade.tryCreateCanvas(
                    this, recipe, type, () -> {
                        refreshList();
                        refreshDerived();
                    });
            jeiCanvasUid = recipe.uid();
        }
        if (jeiCanvas != null) {
            com.zizazr.kjsgen.integration.jei.JeiCanvasFacade.updateValidation(jeiCanvas, issues.stream()
                    .filter(issue -> issue.severity() == RecipeValidator.Severity.ERROR && !issue.slotKey().isEmpty())
                    .map(RecipeValidator.Issue::slotKey)
                    .collect(java.util.stream.Collectors.toSet()));
            canvasHolder.addChild(jeiCanvas);
            return true;
        }
        return false;
    }

    private void addValidationMessages(List<RecipeValidator.Issue> issues) {

        // validation messages under the canvas
        issues.stream().limit(3).forEach(issue -> {
            Label label = new Label();
            label.setText(Component.translatable(issue.messageKey(), issue.args()));
            label.textStyle(style -> style
                    .textColor(issue.severity() == RecipeValidator.Severity.ERROR ? 0xffff5555 : 0xffffcc55)
                    .textWrap(TextWrap.WRAP)
                    .adaptiveHeight(true));
            validationBox.addChild(label);
        });
    }

    private UIElement buildDecoration(LayoutDecoration decoration) {
        UIElement element;
        switch (decoration.type()) {
            case ARROW -> {
                element = new UIElement();
                element.layout(layout -> layout.width(24).height(16));
                element.style(style -> style.background(
                        SpriteTexture.of("minecraft:textures/gui/sprites/container/furnace/burn_progress.png")));
            }
            case FLAME -> {
                element = new UIElement();
                element.layout(layout -> layout.width(14).height(14));
                element.style(style -> style.background(
                        SpriteTexture.of("minecraft:textures/gui/sprites/container/furnace/lit_progress.png")));
            }
            case PLUS -> {
                TextElement text = new TextElement();
                text.setText("+", false);
                element = text;
            }
            default -> {
                TextElement text = new TextElement();
                text.setText(decoration.text(), false);
                element = text;
            }
        }
        element.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(decoration.x())
                .top(decoration.y()));
        return element;
    }

    private UIElement buildCanvasSlot(RecipeInstance recipe, RecipeTypeDefinition type,
                                      SlotDefinition slotDef, List<RecipeValidator.Issue> issues) {
        SlotContent content = recipe.slot(slotDef.key());

        UIElement wrapper = new UIElement();
        wrapper.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(slotDef.x())
                .top(slotDef.y())
                .width(18)
                .height(18));

        wrapper.addChild(ContentSlotFactory.build(content));

        boolean hasIssue = issues.stream().anyMatch(issue -> issue.slotKey().equals(slotDef.key()));
        if (hasIssue) {
            UIElement highlight = new UIElement();
            highlight.layout(layout -> layout
                    .positionType(TaffyPosition.ABSOLUTE)
                    .left(0).top(0).width(18).height(18));
            highlight.style(style -> style.background(ColorPattern.RED.borderTexture(1)));
            wrapper.addChild(highlight);
        }

        Component roleName = Component.translatable("kjsgen.slot_role." + slotDef.role().name().toLowerCase());
        wrapper.style(style -> style.tooltips(
                Component.literal(slotDef.key() + " · ").append(roleName),
                Component.literal(content.describe()),
                Component.translatable("kjsgen.ui.slot_hint")));

        wrapper.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            event.stopPropagation();
            if (event.button == 1) {
                recipe.setSlot(slotDef.key(), SlotContent.EMPTY);
                refreshList();
                refreshDerived();
            } else {
                SlotEditDialog.show(this, recipe, slotDef, () -> {
                    refreshList();
                    refreshDerived();
                });
            }
        }, true);
        return wrapper;
    }

    // ------------------------------------------------------------------ parameter panel

    void rebuildParams() {
        paramsPanel.viewContainer(UIElement::clearAllChildren);
        RecipeInstance recipe = selectedRecipe().orElse(null);
        if (recipe == null) {
            return;
        }
        RecipeTypeDefinition type = RecipeTypeRegistry.get(recipe.typeId()).orElse(null);
        if (type == null) {
            return;
        }

        paramsPanel.addScrollViewChild(paramRow("kjsgen.ui.recipe_id",
                textField(recipe.recipeId(), "namespace:path", value -> {
                    recipe.setRecipeId(value);
                    refreshDerived();
                })));
        paramsPanel.addScrollViewChild(paramRow("kjsgen.ui.group",
                textField(recipe.group(), "", value -> {
                    recipe.setGroup(value);
                    refreshDerived();
                })));
        paramsPanel.addScrollViewChild(paramRow("kjsgen.ui.comment",
                textField(recipe.comment(), "", value -> {
                    recipe.setComment(value);
                    refreshDerived();
                })));
        paramsPanel.addScrollViewChild(paramRow("kjsgen.ui.target_file",
                textField(recipe.targetFile(), project.defaultTargetFile(), value -> {
                    recipe.setTargetFile(value);
                    refreshDerived();
                })));
        paramsPanel.addScrollViewChild(paramRow("kjsgen.ui.if_mod_loaded",
                textField(recipe.conditionModLoaded(), "", value -> {
                    recipe.setConditionModLoaded(value);
                    refreshDerived();
                })));

        for (ParameterDefinition param : type.parameters()) {
            if (param.key().startsWith("__")) {
                continue; // hidden parameters (template codegen internals)
            }
            paramsPanel.addScrollViewChild(paramRow(param, recipe, type));
        }
    }

    private TextField textField(String value, String placeholder, java.util.function.Consumer<String> responder) {
        TextField field = new TextField();
        field.setText(value, false);
        field.layout(layout -> layout.flex(1).height(12));
        if (!placeholder.isEmpty()) {
            field.textFieldStyle(style -> style.placeholder(Component.literal(placeholder)));
        }
        field.setTextResponder(responder);
        return field;
    }

    private UIElement paramRow(String labelKey, UIElement control) {
        UIElement row = new UIElement();
        row.layout(layout -> layout
                .flexDirection(FlexDirection.ROW)
                .widthPercent(100)
                .alignItems(AlignItems.CENTER)
                .gapAll(2));
        Label label = new Label();
        label.setText(labelKey);
        label.layout(layout -> layout.width(48));
        label.textStyle(style -> style.fontSize(7).textWrap(TextWrap.WRAP).adaptiveHeight(true));
        row.addChildren(label, control);
        return row;
    }

    private UIElement paramRow(ParameterDefinition param, RecipeInstance recipe, RecipeTypeDefinition type) {
        String value = recipe.param(type, param.key());
        UIElement control = switch (param.type()) {
            case INT -> {
                TextField field = textField(value, param.defaultValue(), v -> {
                    recipe.setParam(param.key(), v);
                    refreshDerived();
                });
                field.setNumbersOnlyInt(0, Integer.MAX_VALUE);
                yield field;
            }
            case FLOAT -> {
                TextField field = textField(value, param.defaultValue(), v -> {
                    recipe.setParam(param.key(), v);
                    refreshDerived();
                });
                field.setNumbersOnlyFloat(0f, Float.MAX_VALUE);
                yield field;
            }
            case BOOL -> {
                Toggle toggle = new Toggle();
                toggle.noText();
                toggle.setOn(Boolean.parseBoolean(value));
                toggle.setOnToggleChanged(on -> {
                    recipe.setParam(param.key(), Boolean.toString(on));
                    refreshDerived();
                });
                yield toggle;
            }
            case ENUM -> {
                Selector<String> selector = new Selector<>();
                selector.setCandidates(param.options());
                selector.setSelected(value.isEmpty() ? param.defaultValue() : value, false);
                selector.setOnValueChanged(v -> {
                    recipe.setParam(param.key(), v);
                    refreshDerived();
                });
                selector.layout(layout -> layout.flex(1));
                yield selector;
            }
            default -> textField(value, param.defaultValue(), v -> {
                recipe.setParam(param.key(), v);
                refreshDerived();
            });
        };
        UIElement row = new UIElement();
        row.layout(layout -> layout
                .flexDirection(FlexDirection.ROW)
                .widthPercent(100)
                .alignItems(AlignItems.CENTER)
                .gapAll(2));
        Label label = new Label();
        label.setText(UiTexts.paramName(param.key()));
        label.layout(layout -> layout.width(48));
        label.textStyle(style -> style.fontSize(7).textWrap(TextWrap.WRAP).adaptiveHeight(true));
        row.addChildren(label, control);
        return row;
    }

    // ------------------------------------------------------------------ preview

    void refreshPreview() {
        String text = selectedRecipe()
                .map(recipe -> ScriptAssembler.previewSingle(project, recipe))
                .orElse("");
        previewArea.setLines(List.of(text.split("\n", -1)));
    }
}
