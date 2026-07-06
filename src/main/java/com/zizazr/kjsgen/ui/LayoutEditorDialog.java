package com.zizazr.kjsgen.ui;

import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib2.gui.texture.SpriteTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.zizazr.kjsgen.core.LayoutDecoration;
import com.zizazr.kjsgen.core.ParameterDefinition;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import com.zizazr.kjsgen.core.SlotDefinition;
import com.zizazr.kjsgen.core.SlotRole;
import com.zizazr.kjsgen.templates.UserLayoutStore;
import dev.vfyjxf.taffy.style.AlignContent;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Visual layout editor for a recipe type: starts from the current layout
 * (for JEI-imported types that is the exact JEI arrangement) and lets the
 * user drag, delete and add slots/decorations, change slot keys (the codegen
 * mapping), roles and content flags, resize the canvas and edit the KubeJS
 * template. Saving registers the modified {@link RecipeTypeDefinition} and
 * persists it to {@code kjsgen/layouts/}, so it survives restarts.
 */
public final class LayoutEditorDialog {
    /** Mutable working copy of a slot. */
    private static final class MutSlot {
        String key;
        SlotRole role;
        int x, y;
        boolean required, item, tag, fluid, count, chance;

        static MutSlot of(SlotDefinition def) {
            MutSlot slot = new MutSlot();
            slot.key = def.key();
            slot.role = def.role();
            slot.x = def.x();
            slot.y = def.y();
            slot.required = def.required();
            slot.item = def.allowsItem();
            slot.tag = def.allowsTag();
            slot.fluid = def.allowsFluid();
            slot.count = def.allowsCount();
            slot.chance = def.allowsChance();
            return slot;
        }

        SlotDefinition toDefinition() {
            return new SlotDefinition(key, role, x, y, required, item, tag, fluid, count, chance);
        }
    }

    /** Mutable working copy of a decoration. */
    private static final class MutDeco {
        LayoutDecoration.Type type;
        int x, y;
        String text = "";

        static MutDeco of(LayoutDecoration deco) {
            MutDeco mut = new MutDeco();
            mut.type = deco.type();
            mut.x = deco.x();
            mut.y = deco.y();
            mut.text = deco.text();
            return mut;
        }
    }

    // editor state
    private final KjsGenUI ui;
    private final RecipeTypeDefinition type;
    private final List<MutSlot> slots = new ArrayList<>();
    private final List<MutDeco> decorations = new ArrayList<>();
    private int canvasWidth, canvasHeight;
    private String templateDefault;

    private Dialog dialog;
    private UIElement canvas;
    private UIElement workbench;
    private ScrollerView propsPanel;
    private Object selected; // MutSlot | MutDeco | null

    // live-JEI edit canvas (nativeKeys = slots that belong to the real JEI layout)
    private UIElement jeiCanvas;
    private Set<String> nativeKeys = Set.of();
    private boolean jeiCanvasResolved;

    // drag state (overlay elements; native JEI slots are dragged by the canvas itself)
    private Object dragged;
    private float grabDX, grabDY;

    private LayoutEditorDialog(KjsGenUI ui, RecipeTypeDefinition type) {
        this.ui = ui;
        this.type = type;
        type.slots().forEach(def -> slots.add(MutSlot.of(def)));
        type.decorations().forEach(deco -> decorations.add(MutDeco.of(deco)));
        this.canvasWidth = type.canvasWidth();
        this.canvasHeight = type.canvasHeight();
        this.templateDefault = type.parameter("template")
                .or(() -> type.parameter("__template"))
                .map(ParameterDefinition::defaultValue)
                .orElse(null);
    }

    public static void show(KjsGenUI ui, RecipeTypeDefinition type) {
        new LayoutEditorDialog(ui, type).open();
    }

    // ------------------------------------------------------------------ dialog

    private void open() {
        dialog = new Dialog();
        dialog.setTitle(Component.translatable("kjsgen.ui.layout_editor", type.id()).getString());
        dialog.darkenBackground();
        dialog.overlay.layout(layout -> layout.width(400));

        UIElement content = new UIElement();
        content.layout(layout -> layout
                .widthPercent(100)
                .flexDirection(FlexDirection.COLUMN)
                .gapAll(3));

        // ---- main row: workbench (canvas) + properties
        UIElement mainRow = new UIElement();
        mainRow.layout(layout -> layout
                .widthPercent(100)
                .flexDirection(FlexDirection.ROW)
                .gapAll(3));

        workbench = new UIElement();
        workbench.layout(layout -> layout
                .flex(1)
                .height(150)
                .alignItems(AlignItems.CENTER)
                .justifyContent(AlignContent.CENTER));
        workbench.addClass("panel_bg");
        // background click deselects; drag is tracked at workbench level so it
        // keeps working when the cursor leaves the dragged element
        workbench.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            selected = null;
            rebuildProps();
            rebuildCanvas();
        });
        workbench.addEventListener(UIEvents.MOUSE_MOVE, this::onDragMove);
        workbench.addEventListener(UIEvents.MOUSE_UP, event -> endDrag());
        workbench.addEventListener(UIEvents.MOUSE_LEAVE, event -> endDrag());

        propsPanel = new ScrollerView();
        propsPanel.layout(layout -> layout.width(130).height(150));
        propsPanel.viewContainer(view -> view.getLayout().gapAll(2));

        mainRow.addChildren(workbench, propsPanel);
        content.addChild(mainRow);

        // ---- add buttons + canvas size
        UIElement toolsRow = new UIElement();
        toolsRow.layout(layout -> layout
                .widthPercent(100)
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER)
                .gapAll(3));
        toolsRow.addChildren(
                new Button().setText("kjsgen.ui.add_slot").setOnClick(e -> addSlot()),
                new Button().setText("kjsgen.ui.add_arrow").setOnClick(e -> addDecoration(LayoutDecoration.Type.ARROW)),
                new Button().setText("kjsgen.ui.add_text").setOnClick(e -> addDecoration(LayoutDecoration.Type.TEXT)),
                smallLabel("kjsgen.ui.canvas_size"),
                intField(canvasWidth, 18, 400, value -> {
                    canvasWidth = value;
                    rebuildCanvas();
                }),
                intField(canvasHeight, 18, 400, value -> {
                    canvasHeight = value;
                    rebuildCanvas();
                })
        );
        content.addChild(toolsRow);

        // ---- KubeJS template of the type (template codegen only)
        if (templateDefault != null) {
            UIElement templateRow = new UIElement();
            templateRow.layout(layout -> layout
                    .widthPercent(100)
                    .flexDirection(FlexDirection.ROW)
                    .alignItems(AlignItems.CENTER)
                    .gapAll(2));
            TextField templateField = new TextField();
            templateField.setText(templateDefault, false);
            templateField.layout(layout -> layout.flex(1).height(12));
            templateField.setTextResponder(value -> templateDefault = value);
            templateRow.addChildren(smallLabel("kjsgen.param.template"), templateField);
            content.addChild(templateRow);
        } else {
            // built-in generators address slots by fixed keys
            Label warn = smallLabel("kjsgen.ui.layout_warn_builtin");
            warn.textStyle(style -> style.textColor(0xffffcc55).textWrap(TextWrap.WRAP).adaptiveHeight(true));
            content.addChild(warn);
        }

        dialog.addContent(content);
        dialog.addButton(new Button().setText("kjsgen.ui.save").setOnClick(e -> save()));
        dialog.addButton(new Button().setText("kjsgen.ui.cancel").setOnClick(e -> dialog.close()));

        rebuildCanvas();
        rebuildProps();
        dialog.show(ui.root());
    }

    // ------------------------------------------------------------------ canvas

    private void rebuildCanvas() {
        workbench.clearAllChildren();
        resolveJeiCanvas();

        if (jeiCanvas != null) {
            // live JEI mode: the panel + its slots are rendered/dragged by the
            // canvas itself; only decorations and user-added slots are overlays
            canvas = jeiCanvas;
            canvas.getLayout().width(canvasWidth).height(canvasHeight);
            canvas.clearAllChildren();
            for (MutDeco deco : decorations) {
                canvas.addChild(decoElement(deco));
            }
            for (MutSlot slot : slots) {
                if (!nativeKeys.contains(slot.key)) {
                    canvas.addChild(slotElement(slot));
                }
            }
            workbench.addChild(canvas);
            return;
        }

        canvas = new UIElement();
        canvas.layout(layout -> layout.width(canvasWidth).height(canvasHeight));
        canvas.style(style -> style.background(ColorPattern.T_GRAY.borderTexture(1)));

        for (MutDeco deco : decorations) {
            canvas.addChild(decoElement(deco));
        }
        for (MutSlot slot : slots) {
            canvas.addChild(slotElement(slot));
        }
        workbench.addChild(canvas);
    }

    /** Builds the live-JEI edit canvas once, when JEI knows this recipe type. */
    private void resolveJeiCanvas() {
        if (jeiCanvasResolved) {
            return;
        }
        jeiCanvasResolved = true;
        if (!com.zizazr.kjsgen.KjsGen.isJeiLoaded()) {
            return;
        }
        var editCanvas = com.zizazr.kjsgen.integration.jei.JeiCanvasFacade.tryCreateEditCanvas(
                type.id(), canvasWidth, canvasHeight, new LayoutEditHost() {
                    @Override
                    public Set<String> slotKeys() {
                        Set<String> keys = new HashSet<>();
                        slots.forEach(slot -> keys.add(slot.key));
                        return keys;
                    }

                    @Override
                    public int slotX(String key) {
                        return findSlot(key).map(slot -> slot.x).orElse(0);
                    }

                    @Override
                    public int slotY(String key) {
                        return findSlot(key).map(slot -> slot.y).orElse(0);
                    }

                    @Override
                    public void moveSlot(String key, int x, int y) {
                        findSlot(key).ifPresent(slot -> {
                            slot.x = x;
                            slot.y = y;
                        });
                    }

                    @Override
                    public void selectSlot(String key) {
                        findSlot(key).ifPresent(slot -> {
                            selected = slot;
                            rebuildProps();
                        });
                    }

                    @Override
                    public void dragEnded() {
                        rebuildProps();
                    }

                    @Override
                    public String selectedSlotKey() {
                        return selected instanceof MutSlot slot ? slot.key : null;
                    }
                });
        if (editCanvas != null) {
            jeiCanvas = editCanvas.element();
            nativeKeys = editCanvas.nativeKeys();
        }
    }

    private java.util.Optional<MutSlot> findSlot(String key) {
        return slots.stream().filter(slot -> slot.key.equals(key)).findFirst();
    }

    private UIElement slotElement(MutSlot slot) {
        UIElement element = new UIElement();
        element.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(slot.x)
                .top(slot.y)
                .width(18)
                .height(18));
        element.style(style -> style.backgroundTexture(ItemSlot.ITEM_SLOT_TEXTURE));

        // role tint + selection border
        int roleColor = switch (slot.role) {
            case OUTPUT -> 0xffffaa00;
            case CATALYST -> 0xff55ffff;
            default -> 0xffffffff;
        };
        UIElement border = new UIElement();
        border.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(0).top(0).width(18).height(18));
        int borderColor = slot == selected ? 0xff55ff55 : roleColor;
        border.style(style -> style.background(new ColorBorderTexture(1, borderColor)));
        element.addChild(border);

        TextElement key = new TextElement();
        key.setText(slot.key, false);
        key.textStyle(style -> style.fontSize(6));
        key.layout(layout -> layout.left(1).top(6));
        key.setOverflowVisible(false);
        element.addChild(key);

        element.style(style -> style.tooltips(
                Component.literal(slot.key + " · " + slot.role),
                Component.translatable("kjsgen.ui.layout_drag_hint")));
        element.addEventListener(UIEvents.MOUSE_DOWN, event -> beginDrag(slot, element, event), true);
        return element;
    }

    private UIElement decoElement(MutDeco deco) {
        UIElement element;
        switch (deco.type) {
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
            default -> {
                TextElement text = new TextElement();
                text.setText(deco.text.isEmpty() ? (deco.type == LayoutDecoration.Type.PLUS ? "+" : "text") : deco.text, false);
                element = text;
            }
        }
        element.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .left(deco.x)
                .top(deco.y));
        if (deco == selected) {
            element.style(style -> style.backgroundTexture(ColorPattern.GREEN.borderTexture(1)));
        }
        element.style(style -> style.tooltips(Component.translatable("kjsgen.ui.layout_drag_hint")));
        element.addEventListener(UIEvents.MOUSE_DOWN, event -> beginDrag(deco, element, event), true);
        return element;
    }

    // ------------------------------------------------------------------ dragging

    private void beginDrag(Object model, UIElement element, UIEvent event) {
        event.stopPropagation();
        selected = model;
        dragged = model;
        grabDX = event.x - element.getPositionX();
        grabDY = event.y - element.getPositionY();
        rebuildProps();
        rebuildCanvas();
    }

    private void onDragMove(UIEvent event) {
        if (dragged == null || canvas == null) {
            return;
        }
        int newX = Math.round(event.x - grabDX - canvas.getPositionX());
        int newY = Math.round(event.y - grabDY - canvas.getPositionY());
        if (dragged instanceof MutSlot slot) {
            slot.x = clamp(newX, canvasWidth - 18);
            slot.y = clamp(newY, canvasHeight - 18);
        } else if (dragged instanceof MutDeco deco) {
            deco.x = clamp(newX, canvasWidth - 8);
            deco.y = clamp(newY, canvasHeight - 8);
        }
        rebuildCanvas();
    }

    private void endDrag() {
        if (dragged != null) {
            dragged = null;
            rebuildProps(); // refresh x/y fields after the move
        }
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }

    // ------------------------------------------------------------------ properties panel

    private void rebuildProps() {
        propsPanel.viewContainer(UIElement::clearAllChildren);
        if (selected instanceof MutSlot slot) {
            buildSlotProps(slot);
        } else if (selected instanceof MutDeco deco) {
            buildDecoProps(deco);
        } else {
            Label hint = smallLabel("kjsgen.ui.layout_hint");
            hint.textStyle(style -> style.textWrap(TextWrap.WRAP).adaptiveHeight(true));
            propsPanel.addScrollViewChild(hint);
        }
    }

    private void buildSlotProps(MutSlot slot) {
        TextField keyField = new TextField();
        keyField.setText(slot.key, false);
        keyField.layout(layout -> layout.flex(1).height(12));
        keyField.setTextResponder(value -> {
            slot.key = value.trim();
            rebuildCanvas();
        });
        propsPanel.addScrollViewChild(labeled("kjsgen.ui.slot_key", keyField));

        Selector<SlotRole> roleSelector = new Selector<>();
        roleSelector.setCandidates(List.of(SlotRole.values()));
        roleSelector.setSelected(slot.role, false);
        roleSelector.setOnValueChanged(role -> {
            slot.role = role;
            rebuildCanvas();
        });
        roleSelector.layout(layout -> layout.flex(1));
        propsPanel.addScrollViewChild(labeled("kjsgen.ui.slot_role", roleSelector));

        propsPanel.addScrollViewChild(labeled("X", intField(slot.x, 0, 400, value -> {
            slot.x = value;
            rebuildCanvas();
        })));
        propsPanel.addScrollViewChild(labeled("Y", intField(slot.y, 0, 400, value -> {
            slot.y = value;
            rebuildCanvas();
        })));

        propsPanel.addScrollViewChild(toggle("kjsgen.ui.slot_required", slot.required, on -> slot.required = on));
        propsPanel.addScrollViewChild(toggle("kjsgen.ui.slot_item", slot.item, on -> slot.item = on));
        propsPanel.addScrollViewChild(toggle("kjsgen.ui.slot_tag", slot.tag, on -> slot.tag = on));
        propsPanel.addScrollViewChild(toggle("kjsgen.ui.slot_fluid", slot.fluid, on -> slot.fluid = on));
        propsPanel.addScrollViewChild(toggle("kjsgen.ui.slot_count", slot.count, on -> slot.count = on));
        propsPanel.addScrollViewChild(toggle("kjsgen.ui.slot_chance", slot.chance, on -> slot.chance = on));

        propsPanel.addScrollViewChild(new Button()
                .setText("kjsgen.ui.delete")
                .setOnClick(e -> {
                    slots.remove(slot);
                    selected = null;
                    rebuildProps();
                    rebuildCanvas();
                }));
    }

    private void buildDecoProps(MutDeco deco) {
        propsPanel.addScrollViewChild(smallLabel(deco.type.name()));
        if (deco.type == LayoutDecoration.Type.TEXT) {
            TextField textField = new TextField();
            textField.setText(deco.text, false);
            textField.layout(layout -> layout.flex(1).height(12));
            textField.setTextResponder(value -> {
                deco.text = value;
                rebuildCanvas();
            });
            propsPanel.addScrollViewChild(labeled("kjsgen.ui.deco_text", textField));
        }
        propsPanel.addScrollViewChild(labeled("X", intField(deco.x, 0, 400, value -> {
            deco.x = value;
            rebuildCanvas();
        })));
        propsPanel.addScrollViewChild(labeled("Y", intField(deco.y, 0, 400, value -> {
            deco.y = value;
            rebuildCanvas();
        })));
        propsPanel.addScrollViewChild(new Button()
                .setText("kjsgen.ui.delete")
                .setOnClick(e -> {
                    decorations.remove(deco);
                    selected = null;
                    rebuildProps();
                    rebuildCanvas();
                }));
    }

    // ------------------------------------------------------------------ actions

    private void addSlot() {
        MutSlot slot = new MutSlot();
        slot.key = freeKey("in");
        slot.role = SlotRole.INPUT;
        slot.x = 2;
        slot.y = 2;
        slot.item = true;
        slot.tag = true;
        slots.add(slot);
        selected = slot;
        rebuildProps();
        rebuildCanvas();
    }

    private void addDecoration(LayoutDecoration.Type decoType) {
        MutDeco deco = new MutDeco();
        deco.type = decoType;
        deco.x = 2;
        deco.y = 2;
        deco.text = decoType == LayoutDecoration.Type.TEXT ? "text" : "";
        decorations.add(deco);
        selected = deco;
        rebuildProps();
        rebuildCanvas();
    }

    private String freeKey(String prefix) {
        Set<String> used = new HashSet<>();
        slots.forEach(slot -> used.add(slot.key));
        for (int i = 0; ; i++) {
            String key = prefix + i;
            if (!used.contains(key)) {
                return key;
            }
        }
    }

    private void save() {
        // keys must be non-empty and unique: the codegen mapping depends on them
        Set<String> keys = new HashSet<>();
        for (MutSlot slot : slots) {
            if (slot.key.isEmpty() || !keys.add(slot.key)) {
                Dialog.showNotification(Component.translatable("kjsgen.ui.layout_bad_keys").getString(), 3f)
                        .show(ui.root());
                return;
            }
        }

        List<SlotDefinition> newSlots = slots.stream().map(MutSlot::toDefinition).toList();
        List<LayoutDecoration> newDecos = decorations.stream()
                .map(deco -> new LayoutDecoration(deco.type, deco.x, deco.y, deco.text))
                .toList();

        List<ParameterDefinition> newParams = new ArrayList<>();
        for (ParameterDefinition param : type.parameters()) {
            if (templateDefault != null
                    && (param.key().equals("template") || param.key().equals("__template"))) {
                newParams.add(new ParameterDefinition(param.key(), param.type(), templateDefault, param.options()));
            } else {
                newParams.add(param);
            }
        }

        RecipeTypeDefinition updated = new RecipeTypeDefinition(
                type.id(), type.modId(), type.iconItem(),
                canvasWidth, canvasHeight,
                newSlots, newDecos, List.copyOf(newParams),
                type.codegenId(), type.requiresMod(), type.editorKind(), type.jeiCategory());
        RecipeTypeRegistry.register(updated);
        UserLayoutStore.save(updated);

        dialog.close();
        ui.invalidateJeiCanvas();
        ui.refreshAll();
        Dialog.showNotification(Component.translatable("kjsgen.ui.layout_saved").getString(), 2f).show(ui.root());
    }

    // ------------------------------------------------------------------ small helpers

    private static Label smallLabel(String key) {
        Label label = new Label();
        label.setText(key);
        label.textStyle(style -> style.fontSize(7));
        return label;
    }

    private UIElement labeled(String key, UIElement control) {
        UIElement row = new UIElement();
        row.layout(layout -> layout
                .flexDirection(FlexDirection.ROW)
                .widthPercent(100)
                .alignItems(AlignItems.CENTER)
                .gapAll(2));
        Label label = smallLabel(key);
        label.layout(layout -> layout.width(40));
        row.addChildren(label, control);
        return row;
    }

    private TextField intField(int value, int min, int max, java.util.function.IntConsumer responder) {
        TextField field = new TextField();
        field.setText(Integer.toString(value), false);
        field.setNumbersOnlyInt(min, max);
        field.layout(layout -> layout.width(36).height(12));
        field.setTextResponder(text -> {
            try {
                responder.accept(Integer.parseInt(text.trim()));
            } catch (NumberFormatException ignored) {
            }
        });
        return field;
    }

    private UIElement toggle(String key, boolean initial, java.util.function.Consumer<Boolean> responder) {
        Toggle toggle = new Toggle();
        toggle.setText(key);
        toggle.setOn(initial);
        toggle.setOnToggleChanged(responder::accept);
        return toggle;
    }
}
