package com.zizazr.kjsgen.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.zizazr.kjsgen.KjsGen;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.RecipeTypeRegistry;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.integration.jei.JeiLayoutImporter;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.FlexWrap;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * JEI-like grid of recipe type cards with a name/mod filter.
 */
public final class TypePickerDialog {
    private TypePickerDialog() {
    }

    public static void show(KjsGenUI ui, Consumer<RecipeTypeDefinition> onPick) {
        Dialog dialog = new Dialog();
        dialog.setTitle(Component.translatable("kjsgen.ui.pick_type").getString());
        dialog.darkenBackground();
        dialog.overlay.layout(layout -> layout.width(250));

        UIElement content = new UIElement();
        content.layout(layout -> layout
                .widthPercent(100)
                .flexDirection(FlexDirection.COLUMN)
                .gapAll(3));

        TextField searchField = new TextField();
        searchField.layout(layout -> layout.widthPercent(100).height(14));
        searchField.textFieldStyle(style -> style.placeholder(Component.translatable("kjsgen.ui.search_types")));

        ScrollerView grid = new ScrollerView();
        grid.layout(layout -> layout.widthPercent(100).height(150));
        grid.viewContainer(view -> view.getLayout()
                .flexDirection(FlexDirection.ROW)
                .flexWrap(FlexWrap.WRAP)
                .gapAll(3));

        Runnable refresh = () -> {
            String query = searchField.getValue() == null ? "" : searchField.getValue().toLowerCase().trim();
            grid.viewContainer(UIElement::clearAllChildren);
            for (RecipeTypeDefinition type : RecipeTypeRegistry.all()) {
                if (!type.isAvailable()) {
                    continue;
                }
                String name = UiTexts.typeName(type).getString();
                if (!query.isEmpty()
                        && !name.toLowerCase().contains(query)
                        && !type.id().contains(query)
                        && !type.modId().contains(query)) {
                    continue;
                }
                grid.addScrollViewChild(buildCard(type, () -> {
                    dialog.close();
                    onPick.accept(type);
                }));
            }
        };
        searchField.setTextResponder(text -> refresh.run());
        refresh.run();

        content.addChildren(searchField, grid);
        dialog.addContent(content);
        // reads the layouts other mods registered in their JEI plugins
        if (KjsGen.isJeiLoaded()) {
            dialog.addButton(new Button()
                    .setText("kjsgen.ui.import_jei")
                    .setOnClick(e -> {
                        importFromJei(ui);
                        refresh.run();
                    }));
        }
        dialog.addButton(new Button().setText("kjsgen.ui.cancel").setOnClick(e -> dialog.close()));
        dialog.show(ui.root());
    }

    /**
     * Only called when JEI is installed, so the JEI classes referenced by
     * {@link JeiLayoutImporter} are safe to load here.
     */
    private static void importFromJei(KjsGenUI ui) {
        if (!JeiLayoutImporter.isRuntimeAvailable()) {
            Dialog.showNotification(Component.translatable("kjsgen.ui.import_jei_unavailable").getString(), 3f)
                    .show(ui.root());
            return;
        }
        JeiLayoutImporter.ImportReport report = JeiLayoutImporter.importAll();
        String info = Component.translatable("kjsgen.ui.import_jei_done",
                report.imported(), report.skipped()).getString();
        if (!report.errors().isEmpty()) {
            info += "\n" + Component.translatable("kjsgen.ui.import_jei_errors",
                    String.join(", ", report.errors())).getString();
        }
        Dialog.showNotification(info, 4f).show(ui.root());
    }

    private static UIElement buildCard(RecipeTypeDefinition type, Runnable onClick) {
        UIElement card = new UIElement();
        card.layout(layout -> layout
                .width(54)
                .height(38)
                .flexDirection(FlexDirection.COLUMN)
                .alignItems(AlignItems.CENTER)
                .paddingAll(2)
                .gapAll(1));
        card.addClass("panel_bg");
        card.style(style -> style.tooltips(
                UiTexts.typeName(type),
                Component.literal(type.id()),
                Component.literal("mod: " + type.modId())));

        UIElement icon = new UIElement();
        icon.layout(layout -> layout.width(16).height(16));
        icon.style(style -> style.background(new ItemStackTexture(
                ContentSlotFactory.stackOf(SlotContent.item(type.iconItem(), 1)))));

        Label name = new Label();
        name.setText(UiTexts.typeName(type));
        name.layout(layout -> layout.widthPercent(100).flex(1));
        name.textStyle(style -> style.fontSize(6).textWrap(TextWrap.WRAP).adaptiveHeight(true));
        name.setOverflowVisible(false);

        card.addChildren(icon, name);
        card.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            event.stopPropagation();
            onClick.run();
        }, true);
        return card;
    }
}
