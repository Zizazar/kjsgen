package com.zizazr.kjsgen.ui.vanilla;

import com.zizazr.kjsgen.core.ContentKind;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.core.SlotDefinition;
import com.zizazr.kjsgen.integration.mekanism.MekanismChemicals;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla dialog to edit one slot: pick item / tag / fluid via a synchronous
 * search or a manual id, then set count / amount / chance / components.
 */
public final class VanillaSlotEditScreen extends VanillaDialogScreen {
    private record Entry(String id, ItemStack icon, String label,
                         MekanismChemicals.ChemicalInfo chemical) {
        Entry(String id, ItemStack icon, String label) {
            this(id, icon, label, null);
        }
    }

    private final SlotDefinition slotDef;
    /** Where the edited content is written on OK / Clear (a plain slot or one list entry). */
    private final java.util.function.Consumer<SlotContent> sink;

    // ---- mutable edit state (survives widget rebuilds on kind change) ----
    private ContentKind kind;
    private String id;
    private int count;
    private int amount;
    private float chance;
    private String components;

    // ---- search ----------------------------------------------------------
    private EditBox idField;
    private final List<Entry> results = new ArrayList<>();
    private int resultScroll;
    private int resultsX, resultsY, resultsW, resultsH;

    public VanillaSlotEditScreen(VanillaEditorScreen parent, RecipeInstance recipe, SlotDefinition slotDef) {
        this(parent, slotDef, slotDef.key(), recipe.slot(slotDef.key()),
                content -> recipe.setSlot(slotDef.key(), content));
    }

    /**
     * Edit an arbitrary piece of content, writing the result back through {@code sink}.
     * Used for list-slot entries, where the storage key ("in0", "in1", ...) differs
     * from the layout slot key and clearing must compact the list.
     *
     * @param titleKey label shown in the dialog title (e.g. "in[2]")
     * @param initial  the content currently in that entry (may be {@link SlotContent#EMPTY})
     */
    public VanillaSlotEditScreen(VanillaEditorScreen parent, SlotDefinition slotDef, String titleKey,
                                 SlotContent initial, java.util.function.Consumer<SlotContent> sink) {
        super(parent, Component.translatable("kjsgen.ui.edit_slot", titleKey));
        this.slotDef = slotDef;
        this.sink = sink;
        this.kind = initial.kind() == ContentKind.EMPTY ? firstAllowedKind(slotDef) : initial.kind();
        this.id = initial.id();
        this.count = initial.count();
        this.amount = initial.amount();
        this.chance = initial.chance();
        this.components = initial.components();
    }

    @Override
    protected void init() {
        int dynH = dynamicFieldCount() * 20;
        centerDialog(240, 170 + dynH);

        int x = dialogX + 10;
        int w = dialogW - 20;
        int y = dialogY + 26;

        // kind selector
        List<ContentKind> kinds = allowedKinds(slotDef);
        if (kinds.size() > 1) {
            CycleButton<ContentKind> kindButton = CycleButton.<ContentKind>builder(
                            k -> Component.literal(kindLabel(k)))
                    .withValues(kinds)
                    .withInitialValue(kind)
                    .create(x + 60, y, w - 60, 16, Component.translatable("kjsgen.ui.kind"),
                            (btn, v) -> {
                                this.kind = v;
                                this.resultScroll = 0;
                                rebuildWidgets();
                            });
            addRenderableWidget(kindButton);
        }
        y += 20;

        // search field
        EditBox searchField = new EditBox(this.font, x + 60, y, w - 60, 16,
                Component.translatable("kjsgen.ui.search"));
        searchField.setHint(Component.translatable("kjsgen.ui.search_types"));
        searchField.setResponder(this::runSearch);
        addRenderableWidget(searchField);
        y += 20;

        // results list (custom-drawn, hit-tested in mouseClicked)
        resultsX = x;
        resultsY = y;
        resultsW = w;
        resultsH = 54;
        y += resultsH + 4;

        // manual id
        idField = new EditBox(this.font, x + 60, y, w - 60, 16, Component.translatable("kjsgen.ui.id"));
        idField.setMaxLength(256);
        idField.setValue(id);
        idField.setHint(Component.literal("minecraft:stick"));
        idField.setResponder(v -> this.id = v.trim());
        addRenderableWidget(idField);
        y += 20;

        // dynamic numeric fields
        addDynamicFields(x, w, y);

        // action buttons
        int by = dialogY + dialogH - 24;
        int bw = (w - 8) / 3;
        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.ok"), b -> commit())
                .bounds(x, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.clear"), b -> {
            sink.accept(SlotContent.EMPTY);
            ((VanillaEditorScreen) parent).markDirty();
            onClose();
        }).bounds(x + bw + 4, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("kjsgen.ui.cancel"), b -> onClose())
                .bounds(x + 2 * (bw + 4), by, w - 2 * (bw + 4), 18).build());

        if (results.isEmpty()) {
            runSearch("");
        }
    }

    /** How many numeric fields the current kind needs (drives dialog height). */
    private int dynamicFieldCount() {
        if (kind.hasAmount()) {
            return 1; // amount
        }
        if (kind == ContentKind.ITEM) {
            return 1 + (slotDef.allowsCount() ? 1 : 0) + (slotDef.allowsChance() ? 1 : 0); // + components
        }
        return 0;
    }

    private void addDynamicFields(int x, int w, int y) {
        if (kind.hasAmount()) {
            addIntField(x, w, y, "kjsgen.ui.amount_mb", amount, 1, 1_000_000, v -> amount = v);
        } else if (kind == ContentKind.ITEM) {
            if (slotDef.allowsCount()) {
                addIntField(x, w, y, "kjsgen.ui.count", count, 1, 999, v -> count = v);
                y += 20;
            }
            if (slotDef.allowsChance()) {
                EditBox chanceField = new EditBox(this.font, x + 60, y, w - 60, 16,
                        Component.translatable("kjsgen.ui.chance"));
                chanceField.setValue(Float.toString(chance));
                chanceField.setFilter(s -> s.matches("\\d*\\.?\\d*"));
                chanceField.setResponder(s -> chance = parseFloat(s, chance));
                addRenderableWidget(chanceField);
                y += 20;
            }
            EditBox componentsField = new EditBox(this.font, x + 60, y, w - 60, 16,
                    Component.translatable("kjsgen.ui.components"));
            componentsField.setMaxLength(512);
            componentsField.setValue(components);
            componentsField.setHint(Component.literal("[minecraft:damage=5]"));
            componentsField.setResponder(s -> components = s.trim());
            addRenderableWidget(componentsField);
        }
    }

    private void addIntField(int x, int w, int y, String labelKey, int value, int min, int max,
                             java.util.function.IntConsumer setter) {
        EditBox box = new EditBox(this.font, x + 60, y, w - 60, 16, Component.translatable(labelKey));
        box.setValue(Integer.toString(value));
        box.setFilter(s -> s.matches("\\d*"));
        box.setResponder(s -> {
            try {
                setter.accept(Math.max(min, Math.min(max, Integer.parseInt(s.trim()))));
            } catch (NumberFormatException ignored) {
            }
        });
        addRenderableWidget(box);
    }

    private void commit() {
        sink.accept(buildContent());
        ((VanillaEditorScreen) parent).markDirty();
        onClose();
    }

    private SlotContent buildContent() {
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

    // ------------------------------------------------------------------ search

    private void runSearch(String word) {
        results.clear();
        resultScroll = 0;
        String query = word.toLowerCase().trim();
        int cap = 120;
        switch (kind) {
            case ITEM -> {
                for (ResourceLocation key : BuiltInRegistries.ITEM.keySet()) {
                    if (results.size() >= cap) {
                        break;
                    }
                    Item item = BuiltInRegistries.ITEM.get(key);
                    if (query.isEmpty() || key.toString().contains(query)
                            || item.getDescription().getString().toLowerCase().contains(query)) {
                        results.add(new Entry(key.toString(), new ItemStack(item),
                                item.getDescription().getString()));
                    }
                }
            }
            case ITEM_TAG -> {
                for (var tagKey : BuiltInRegistries.ITEM.getTagNames().toList()) {
                    if (results.size() >= cap) {
                        break;
                    }
                    String tagId = tagKey.location().toString();
                    if (query.isEmpty() || tagId.contains(query)) {
                        List<ItemStack> stacks = VanillaTheme.tagStacks(tagId);
                        results.add(new Entry(tagId, stacks.get(0), "#" + tagId));
                    }
                }
            }
            case FLUID -> {
                for (ResourceLocation key : BuiltInRegistries.FLUID.keySet()) {
                    if (results.size() >= cap) {
                        break;
                    }
                    if (query.isEmpty() || key.toString().contains(query)) {
                        ItemStack bucket = new ItemStack(BuiltInRegistries.FLUID.get(key).getBucket());
                        if (bucket.isEmpty()) {
                            bucket = new ItemStack(Items.BUCKET);
                        }
                        results.add(new Entry(key.toString(), bucket, key.toString()));
                    }
                }
            }
            case FLUID_TAG -> {
                for (var tagKey : BuiltInRegistries.FLUID.getTagNames().toList()) {
                    if (results.size() >= cap) {
                        break;
                    }
                    String tagId = tagKey.location().toString();
                    if (query.isEmpty() || tagId.contains(query)) {
                        results.add(new Entry(tagId, new ItemStack(Items.WATER_BUCKET), "#" + tagId));
                    }
                }
            }
            case CHEMICAL -> {
                for (var info : MekanismChemicals.search(query, cap)) {
                    results.add(new Entry(info.id(), ItemStack.EMPTY,
                            info.name() + " (" + info.id() + ")", info));
                }
            }
            case CHEMICAL_TAG -> {
                for (var info : MekanismChemicals.searchTags(query, cap)) {
                    results.add(new Entry(info.id(), ItemStack.EMPTY, info.name(), info));
                }
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ render

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // labels for each row (widgets sit at x+60)
        int x = dialogX + 10;
        g.drawString(this.font, Component.translatable("kjsgen.ui.kind"), x, dialogY + 30, VanillaTheme.TEXT_DIM, true);
        g.drawString(this.font, Component.translatable("kjsgen.ui.search"), x, dialogY + 50, VanillaTheme.TEXT_DIM, true);
        g.drawString(this.font, Component.translatable("kjsgen.ui.id"),
                x, resultsY + resultsH + 8, VanillaTheme.TEXT_DIM, true);

        // results list
        VanillaTheme.section(g, resultsX, resultsY, resultsW, resultsH);
        g.enableScissor(resultsX + 1, resultsY + 1, resultsX + resultsW - 1, resultsY + resultsH - 1);
        int rowH = 18;
        for (int i = 0; i < results.size(); i++) {
            int ry = resultsY + i * rowH - resultScroll;
            if (ry + rowH < resultsY || ry > resultsY + resultsH) {
                continue;
            }
            Entry entry = results.get(i);
            boolean hovered = mouseX >= resultsX && mouseX < resultsX + resultsW
                    && mouseY >= Math.max(ry, resultsY) && mouseY < Math.min(ry + rowH, resultsY + resultsH);
            if (hovered) {
                g.fill(resultsX + 1, ry, resultsX + resultsW - 1, ry + rowH, VanillaTheme.ROW_HOVER);
            }
            if (entry.chemical() != null) {
                VanillaTheme.drawChemical(g, resultsX + 2, ry + 1, entry.chemical());
            } else {
                g.renderItem(entry.icon(), resultsX + 2, ry + 1);
            }
            String label = this.font.plainSubstrByWidth(entry.label(), resultsW - 24);
            g.drawString(this.font, label, resultsX + 22, ry + 5, VanillaTheme.TEXT, true);
        }
        g.disableScissor();
        if (results.isEmpty()) {
            g.drawString(this.font, "...", resultsX + 6, resultsY + 4, VanillaTheme.TEXT_DIM, true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (mouseX >= resultsX && mouseX < resultsX + resultsW
                && mouseY >= resultsY && mouseY < resultsY + resultsH) {
            int rowH = 18;
            int index = (int) ((mouseY - resultsY + resultScroll) / rowH);
            if (index >= 0 && index < results.size()) {
                this.id = results.get(index).id();
                if (idField != null) {
                    idField.setValue(this.id);
                }
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (super.mouseScrolled(mouseX, mouseY, scrollX, scrollY)) {
            return true;
        }
        if (mouseX >= resultsX && mouseX < resultsX + resultsW
                && mouseY >= resultsY && mouseY < resultsY + resultsH) {
            int max = Math.max(0, results.size() * 18 - resultsH);
            resultScroll = Math.max(0, Math.min(resultScroll + (int) (-scrollY * 18), max));
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ helpers

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
        if (slotDef.allowsChemical() && MekanismChemicals.available()) {
            kinds.add(ContentKind.CHEMICAL);
            if (slotDef.allowsTag()) {
                kinds.add(ContentKind.CHEMICAL_TAG);
            }
        }
        return kinds;
    }

    private static ContentKind firstAllowedKind(SlotDefinition slotDef) {
        List<ContentKind> kinds = allowedKinds(slotDef);
        return kinds.isEmpty() ? ContentKind.ITEM : kinds.get(0);
    }

    private static String kindLabel(ContentKind kind) {
        return switch (kind) {
            case ITEM -> "item";
            case ITEM_TAG -> "item tag";
            case FLUID -> "fluid";
            case FLUID_TAG -> "fluid tag";
            case CHEMICAL -> "chemical";
            case CHEMICAL_TAG -> "chemical tag";
            case EMPTY -> "-";
        };
    }

    private static float parseFloat(String value, float fallback) {
        try {
            return Float.parseFloat(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
