package com.zizazr.kjsgen.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.SearchComponent;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Selector;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.gui.ui.utils.UIElementProvider;
import com.lowdragmc.lowdraglib2.utils.search.IResultHandler;
import com.zizazr.kjsgen.core.ContentKind;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.core.SlotDefinition;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Dialog for editing one slot: pick item / tag / fluid via search or manual id,
 * set count / amount / chance / components.
 */
public final class SlotEditDialog {
    /** One search hit: registry/tag id + preview stack + display name. */
    record Entry(String id, ItemStack icon, Component name) {
    }

    private SlotEditDialog() {
    }

    public static void show(KjsGenUI ui, RecipeInstance recipe, SlotDefinition slotDef, Runnable onChanged) {
        SlotContent initial = recipe.slot(slotDef.key());

        // mutable edit state
        ContentKind[] kind = {initial.kind() == ContentKind.EMPTY ? firstAllowedKind(slotDef) : initial.kind()};
        String[] id = {initial.id()};
        int[] count = {initial.count()};
        int[] amount = {initial.amount()};
        float[] chance = {initial.chance()};
        String[] components = {initial.components()};

        Dialog dialog = new Dialog();
        dialog.setTitle(Component.translatable("kjsgen.ui.edit_slot", slotDef.key()).getString());
        dialog.darkenBackground();
        dialog.overlay.layout(layout -> layout.width(230));

        UIElement content = new UIElement();
        content.layout(layout -> layout
                .widthPercent(100)
                .flexDirection(FlexDirection.COLUMN)
                .gapAll(3));

        // ---- content kind selector
        List<ContentKind> allowedKinds = allowedKinds(slotDef);
        Selector<ContentKind> kindSelector = new Selector<>();
        kindSelector.setCandidates(allowedKinds);
        kindSelector.setSelected(kind[0], false);
        kindSelector.layout(layout -> layout.widthPercent(100));

        // ---- manual id field
        TextField idField = new TextField();
        idField.setText(id[0], false);
        idField.layout(layout -> layout.widthPercent(100).height(14));
        idField.textFieldStyle(style -> style.placeholder(Component.literal("minecraft:stick")));
        idField.setTextResponder(value -> id[0] = value.trim());

        // ---- dynamic numeric fields (rebuilt when the kind changes)
        UIElement dynamicBox = new UIElement();
        dynamicBox.layout(layout -> layout
                .widthPercent(100)
                .flexDirection(FlexDirection.COLUMN)
                .gapAll(3));

        Runnable rebuildDynamic = () -> {
            dynamicBox.clearAllChildren();
            boolean isFluid = kind[0].isFluid();
            if (isFluid) {
                TextField amountField = new TextField();
                amountField.setText(Integer.toString(amount[0]), false);
                amountField.setNumbersOnlyInt(1, 1_000_000);
                amountField.layout(layout -> layout.flex(1).height(12));
                amountField.setTextResponder(value -> amount[0] = parseInt(value, amount[0]));
                dynamicBox.addChild(labeled("kjsgen.ui.amount_mb", amountField));
            } else if (kind[0] == ContentKind.ITEM) {
                if (slotDef.allowsCount()) {
                    TextField countField = new TextField();
                    countField.setText(Integer.toString(count[0]), false);
                    countField.setNumbersOnlyInt(1, 999);
                    countField.layout(layout -> layout.flex(1).height(12));
                    countField.setTextResponder(value -> count[0] = parseInt(value, count[0]));
                    dynamicBox.addChild(labeled("kjsgen.ui.count", countField));
                }
                if (slotDef.allowsChance()) {
                    TextField chanceField = new TextField();
                    chanceField.setText(Float.toString(chance[0]), false);
                    chanceField.setNumbersOnlyFloat(0f, 1f);
                    chanceField.layout(layout -> layout.flex(1).height(12));
                    chanceField.setTextResponder(value -> chance[0] = parseFloat(value, chance[0]));
                    dynamicBox.addChild(labeled("kjsgen.ui.chance", chanceField));
                }
                TextField componentsField = new TextField();
                componentsField.setText(components[0], false);
                componentsField.layout(layout -> layout.flex(1).height(12));
                componentsField.textFieldStyle(style -> style.placeholder(Component.literal("[minecraft:damage=5]")));
                componentsField.setTextResponder(value -> components[0] = value.trim());
                dynamicBox.addChild(labeled("kjsgen.ui.components", componentsField));
            }
        };

        // ---- search (depends on the kind)
        UIElement searchBox = new UIElement();
        searchBox.layout(layout -> layout.widthPercent(100).height(18));

        Runnable rebuildSearch = () -> {
            searchBox.clearAllChildren();
            SearchComponent<Entry> search = new SearchComponent<>(new SearchComponent.ISearchUI<>() {
                @Override
                public void search(String word, IResultHandler<Entry> handler) {
                    searchEntries(kind[0], word, handler);
                }

                @Override
                @Nonnull
                public String resultText(@Nonnull Entry entry) {
                    return entry.id();
                }

                @Override
                public void onResultSelected(@Nullable Entry entry) {
                    if (entry != null) {
                        id[0] = entry.id();
                        idField.setText(entry.id(), false);
                    }
                }
            });
            search.setCandidateUIProvider(UIElementProvider.iconText(
                    entry -> new ItemStackTexture(entry.icon()),
                    Entry::name));
            search.layout(layout -> layout.widthPercent(100));
            searchBox.addChild(search);
        };

        kindSelector.setOnValueChanged(newKind -> {
            kind[0] = newKind;
            rebuildDynamic.run();
            rebuildSearch.run();
        });

        rebuildDynamic.run();
        rebuildSearch.run();

        content.addChildren(
                labeled("kjsgen.ui.kind", kindSelector),
                labeled("kjsgen.ui.search", searchBox),
                labeled("kjsgen.ui.id", idField),
                dynamicBox
        );
        dialog.addContent(content);

        dialog.addButton(new Button()
                .setText("kjsgen.ui.ok")
                .setOnClick(e -> {
                    recipe.setSlot(slotDef.key(), buildContent(kind[0], id[0], count[0], amount[0], chance[0], components[0]));
                    dialog.close();
                    onChanged.run();
                }));
        dialog.addButton(new Button()
                .setText("kjsgen.ui.clear")
                .setOnClick(e -> {
                    recipe.setSlot(slotDef.key(), SlotContent.EMPTY);
                    dialog.close();
                    onChanged.run();
                }));
        dialog.addButton(new Button()
                .setText("kjsgen.ui.cancel")
                .setOnClick(e -> dialog.close()));

        dialog.show(ui.root());
    }

    // ------------------------------------------------------------------ helpers

    private static UIElement labeled(String key, UIElement control) {
        UIElement row = new UIElement();
        row.layout(layout -> layout
                .flexDirection(FlexDirection.ROW)
                .widthPercent(100)
                .alignItems(AlignItems.CENTER)
                .gapAll(2));
        Label label = new Label();
        label.setText(key);
        label.layout(layout -> layout.width(55));
        label.textStyle(style -> style.fontSize(8));
        row.addChildren(label, control);
        return row;
    }

    private static List<ContentKind> allowedKinds(SlotDefinition slotDef) {
        List<ContentKind> kinds = new ArrayList<>();
        if (slotDef.allowsItem()) {
            kinds.add(ContentKind.ITEM);
            if (slotDef.allowsTag()) {
                kinds.add(ContentKind.ITEM_TAG);
            }
        }
        if (slotDef.allowsFluid()) {
            kinds.add(ContentKind.FLUID);
            if (slotDef.allowsTag()) {
                kinds.add(ContentKind.FLUID_TAG);
            }
        }
        return kinds;
    }

    private static ContentKind firstAllowedKind(SlotDefinition slotDef) {
        List<ContentKind> kinds = allowedKinds(slotDef);
        return kinds.isEmpty() ? ContentKind.ITEM : kinds.get(0);
    }

    private static SlotContent buildContent(ContentKind kind, String id, int count, int amount, float chance, String components) {
        if (id.isBlank()) {
            return SlotContent.EMPTY;
        }
        return switch (kind) {
            case ITEM -> SlotContent.item(id, count).withChance(chance).withComponents(components);
            case ITEM_TAG -> SlotContent.itemTag(id);
            case FLUID -> SlotContent.fluid(id, amount);
            case FLUID_TAG -> SlotContent.fluidTag(id, amount);
            case CHEMICAL -> SlotContent.chemical(id, amount);
            case CHEMICAL_TAG -> SlotContent.chemicalTag(id, amount);
            case EMPTY -> SlotContent.EMPTY;
        };
    }

    private static void searchEntries(ContentKind kind, String word, IResultHandler<Entry> handler) {
        String query = word.toLowerCase();
        switch (kind) {
            case ITEM -> {
                for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
                    if (Thread.currentThread().isInterrupted()) return;
                    Item item = BuiltInRegistries.ITEM.get(key);
                    if (key.toString().contains(query)
                            || item.getDescription().getString().toLowerCase().contains(query)) {
                        handler.acceptResult(new Entry(key.toString(), new ItemStack(item), item.getDescription()));
                    }
                }
            }
            case ITEM_TAG -> BuiltInRegistries.ITEM.getTagNames().forEach(tagKey -> {
                if (Thread.currentThread().isInterrupted()) return;
                String tagId = tagKey.location().toString();
                if (tagId.contains(query)) {
                    List<ItemStack> stacks = ContentSlotFactory.tagStacks(tagId);
                    handler.acceptResult(new Entry(tagId,
                            stacks.isEmpty() ? new ItemStack(Items.BARRIER) : stacks.get(0),
                            Component.literal("#" + tagId)));
                }
            });
            case FLUID, FLUID_TAG -> {
                if (kind == ContentKind.FLUID_TAG) {
                    BuiltInRegistries.FLUID.getTagNames().forEach(tagKey -> {
                        if (Thread.currentThread().isInterrupted()) return;
                        String tagId = tagKey.location().toString();
                        if (tagId.contains(query)) {
                            handler.acceptResult(new Entry(tagId, new ItemStack(Items.WATER_BUCKET),
                                    Component.literal("#" + tagId)));
                        }
                    });
                } else {
                    for (ResourceLocation key : BuiltInRegistries.FLUID.keySet()) {
                        if (Thread.currentThread().isInterrupted()) return;
                        if (key.toString().contains(query)) {
                            ItemStack bucket = new ItemStack(BuiltInRegistries.FLUID.get(key).getBucket());
                            if (bucket.isEmpty()) {
                                bucket = new ItemStack(Items.BUCKET);
                            }
                            handler.acceptResult(new Entry(key.toString(), bucket, Component.literal(key.toString())));
                        }
                    }
                }
            }
            default -> {
            }
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
