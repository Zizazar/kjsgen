package com.zizazr.kjsgen.integration.jei;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.core.SlotContent;
import com.zizazr.kjsgen.core.SlotDefinition;
import com.zizazr.kjsgen.ui.ContentSlotFactory;
import com.zizazr.kjsgen.ui.KjsGenUI;
import com.zizazr.kjsgen.ui.SlotEditDialog;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A canvas element that renders a live JEI recipe category exactly as it appears
 * in JEI (the mod's own background, machine model, arrows, "Heated" text, ...),
 * by holding an {@link IRecipeLayoutDrawable} for a sample recipe and calling
 * {@code drawRecipe} each frame.
 *
 * There are no widgets layered on top of the panel: the user's ingredients are
 * substituted into the real JEI slots through
 * {@link IRecipeSlotDrawable#createDisplayOverrides()}, clicks are resolved with
 * JEI's own hit test ({@link IRecipeLayoutDrawable#getRecipeSlotUnderMouse}),
 * hover highlights come from {@code drawOverlays} and validation errors are
 * shown with the slot's own {@link IRecipeSlotView#drawHighlight}.
 *
 * Only ever instantiated behind a "JEI is loaded" guard (see
 * {@link JeiCanvasFacade}), so the hard JEI references are safe.
 */
public class JeiCanvasElement extends UIElement {
    private final IRecipeLayoutDrawable<?> layout;
    /** JEI slots keyed the same way the importer keys {@link SlotDefinition}s. */
    private final Map<String, IRecipeSlotDrawable> slotsByKey;
    private final RecipeInstance recipe;
    private final RecipeTypeDefinition type;
    private final KjsGenUI ui;
    private final Runnable onChanged;
    private Set<String> invalidKeys = Set.of();
    private long lastTick = 0L;
    /** Set when slot contents changed; overrides are (re)applied lazily in draw. */
    private boolean contentsDirty = true;

    @SuppressWarnings("removal")
    private JeiCanvasElement(IRecipeLayoutDrawable<?> layout,
                             Map<String, IRecipeSlotDrawable> slotsByKey,
                             RecipeInstance recipe,
                             RecipeTypeDefinition type,
                             KjsGenUI ui,
                             Runnable onChanged) {
        this.layout = layout;
        this.slotsByKey = slotsByKey;
        this.recipe = recipe;
        this.type = type;
        this.ui = ui;
        this.onChanged = onChanged;

        getLayout().width(type.canvasWidth()).height(type.canvasHeight());
        addEventListener(UIEvents.MOUSE_DOWN, this::onMouseDown);
        addEventListener(UIEvents.HOVER_TOOLTIPS, this::onHoverTooltips);

        // The definition is authoritative for slot positions (the user may have
        // rearranged the layout in the layout editor): move the real JEI slots
        // to the definition's coordinates, park slots deleted from the
        // definition off-canvas, and overlay slots the user added on top.
        slotsByKey.entrySet().removeIf(entry -> {
            IRecipeSlotDrawable slot = entry.getValue();
            SlotDefinition slotDef = type.slot(entry.getKey()).orElse(null);
            Rect2i rect = slot.getRect();
            Rect2i area = slot.getAreaIncludingBackground();
            if (slotDef == null) {
                slot.setPosition(-10000, -10000);
                return true; // no longer part of the type: skip overrides/clicks
            }
            slot.setPosition(slotDef.x() + (rect.getX() - area.getX()),
                    slotDef.y() + (rect.getY() - area.getY()));
            return false;
        });
        rebuildOverlaySlots();
    }

    /** Static overlay elements for definition slots that don't exist in the JEI layout. */
    private void rebuildOverlaySlots() {
        clearAllChildren();
        for (SlotDefinition slotDef : type.slots()) {
            if (slotsByKey.containsKey(slotDef.key())) {
                continue;
            }
            SlotContent content = recipe.slot(slotDef.key());
            UIElement wrapper = new UIElement();
            wrapper.layout(l -> l
                    .positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                    .left(slotDef.x())
                    .top(slotDef.y())
                    .width(18)
                    .height(18));
            wrapper.addChild(ContentSlotFactory.build(content));
            wrapper.style(style -> style.tooltips(
                    Component.literal(slotDef.key() + ": " + content.describe()),
                    Component.translatable("kjsgen.ui.slot_hint")));
            wrapper.addEventListener(UIEvents.MOUSE_DOWN, event -> {
                event.stopPropagation();
                if (event.button == 1) {
                    recipe.setSlot(slotDef.key(), SlotContent.EMPTY);
                    onChanged.run();
                } else {
                    SlotEditDialog.show(ui, recipe, slotDef, onChanged);
                }
            }, true);
            addChild(wrapper);
        }
    }

    /** Marks slots to be drawn with an error highlight. */
    public void setInvalidKeys(Set<String> invalidKeys) {
        this.invalidKeys = invalidKeys;
    }

    /** Re-applies the recipe's slot contents to the JEI slots on the next frame. */
    public void markContentsDirty() {
        this.contentsDirty = true;
        rebuildOverlaySlots(); // overlay slots render their content themselves
    }

    // ------------------------------------------------------------------ input

    private void onMouseDown(UIEvent event) {
        Optional<String> key = slotKeyAt(event.x, event.y);
        if (key.isEmpty()) {
            return;
        }
        SlotDefinition slotDef = type.slot(key.get()).orElse(null);
        if (slotDef == null) {
            return;
        }
        event.stopPropagation();
        if (event.button == 1) {
            recipe.setSlot(slotDef.key(), SlotContent.EMPTY);
            onChanged.run();
        } else {
            SlotEditDialog.show(ui, recipe, slotDef, onChanged);
        }
    }

    private void onHoverTooltips(UIEvent event) {
        slotKeyAt(event.x, event.y).ifPresent(key -> {
            SlotContent content = recipe.slot(key);
            event.hoverTooltips = HoverTooltips.empty().append(
                    Component.literal(key + ": " + content.describe()),
                    Component.translatable("kjsgen.ui.slot_hint"));
        });
    }

    /** JEI's own hit test (screen coordinates), mapped back to our slot key. */
    private Optional<String> slotKeyAt(double mouseX, double mouseY) {
        return layout.getRecipeSlotUnderMouse(mouseX, mouseY)
                .flatMap(slot -> slotsByKey.entrySet().stream()
                        .filter(entry -> entry.getValue() == slot)
                        .map(Map.Entry::getKey)
                        .findFirst());
    }

    // ------------------------------------------------------------------ factory

    /**
     * Builds a live canvas for the given recipe, or empty when JEI has no
     * matching category / no sample recipe (caller falls back to the static
     * canvas).
     */
    public static Optional<JeiCanvasElement> tryCreate(KjsGenUI ui, RecipeInstance recipe,
                                                       RecipeTypeDefinition type, Runnable onChanged) {
        IJeiRuntime runtime = KjsgenJeiPlugin.runtime();
        if (runtime == null) {
            return Optional.empty();
        }
        IRecipeManager recipeManager = runtime.getRecipeManager();
        IRecipeCategory<?> category = recipeManager.createRecipeCategoryLookup().get()
                .filter(c -> c.getRecipeType().getUid().toString().equals(type.id()))
                .findFirst().orElse(null);
        if (category == null) {
            return Optional.empty();
        }
        IFocusGroup emptyFocus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        return build(ui, recipeManager, category, emptyFocus, recipe, type, onChanged);
    }

    private static <T> Optional<JeiCanvasElement> build(KjsGenUI ui, IRecipeManager recipeManager,
                                                        IRecipeCategory<T> category, IFocusGroup emptyFocus,
                                                        RecipeInstance recipe, RecipeTypeDefinition type,
                                                        Runnable onChanged) {
        Optional<T> sample = recipeManager.createRecipeLookup(category.getRecipeType()).get().findFirst();
        if (sample.isEmpty()) {
            return Optional.empty();
        }
        Optional<IRecipeLayoutDrawable<T>> layout =
                recipeManager.createRecipeLayoutDrawable(category, sample.get(), emptyFocus);
        if (layout.isEmpty()) {
            return Optional.empty();
        }

        // assign the same keys the importer used, so overrides map to the right slot
        Map<String, IRecipeSlotDrawable> slotsByKey = new LinkedHashMap<>();
        int inputs = 0;
        int outputs = 0;
        int catalysts = 0;
        for (IRecipeSlotView slotView : layout.get().getRecipeSlotsView().getSlotViews()) {
            if (!(slotView instanceof IRecipeSlotDrawable drawable)) {
                continue;
            }
            RecipeIngredientRole role = slotView.getRole();
            if (role == RecipeIngredientRole.RENDER_ONLY) {
                continue;
            }
            String key = switch (role) {
                case OUTPUT -> (outputs++ == 0 ? "output" : "output" + outputs);
                case CATALYST -> "catalyst" + catalysts++;
                default -> "in" + inputs++;
            };
            slotsByKey.put(key, drawable);
        }
        if (slotsByKey.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new JeiCanvasElement(layout.get(), slotsByKey, recipe, type, ui, onChanged));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);

        long now = System.currentTimeMillis();
        boolean ticked = false;
        if (now - lastTick >= 50L) {
            layout.tick();
            lastTick = now;
            ticked = true;
        }
        // RecipeLayout.tick() wipes all display overrides once per second (its
        // ingredient-cycle tick), so overrides must be (re)applied after tick,
        // never before — otherwise the sample recipe flashes for one frame.
        if (contentsDirty || ticked) {
            applyDisplayOverrides();
            contentsDirty = false;
        }

        int x = (int) getContentX();
        int y = (int) getContentY();
        layout.setPosition(x, y);
        layout.drawRecipe(guiContext.graphics, guiContext.mouseX, guiContext.mouseY);
        // hover highlight of the slot under the mouse, like in the JEI screen
        layout.drawOverlays(guiContext.graphics, guiContext.mouseX, guiContext.mouseY);

        // validation: error highlight drawn by the JEI slot itself (slot rects are layout-relative)
        if (!invalidKeys.isEmpty()) {
            var pose = guiContext.graphics.pose();
            pose.pushPose();
            pose.translate(x, y, 0);
            slotsByKey.forEach((key, slot) -> {
                if (invalidKeys.contains(key)) {
                    slot.drawHighlight(guiContext.graphics, 0x66FF0000);
                }
            });
            pose.popPose();
        }
    }

    /** Pushes the user's current slot contents into the real JEI slots. */
    private void applyDisplayOverrides() {
        slotsByKey.forEach((key, slot) -> {
            SlotContent content = recipe.slot(key);
            slot.clearDisplayOverrides();
            var consumer = slot.createDisplayOverrides();
            switch (content.kind()) {
                case ITEM -> consumer.addIngredient(VanillaTypes.ITEM_STACK, ContentSlotFactory.stackOf(content));
                case ITEM_TAG -> consumer.addIngredients(VanillaTypes.ITEM_STACK,
                        new ArrayList<>(ContentSlotFactory.tagStacks(content.id())));
                case FLUID -> consumer.addIngredient(NeoForgeTypes.FLUID_STACK, fluidStack(content));
                case FLUID_TAG -> consumer.addIngredient(NeoForgeTypes.FLUID_STACK,
                        new FluidStack(Fluids.WATER, content.amount()));
                case EMPTY -> consumer.addIngredients(VanillaTypes.ITEM_STACK, List.of());
            }
        });
    }

    private static FluidStack fluidStack(SlotContent content) {
        ResourceLocation rl = ResourceLocation.tryParse(content.id());
        Fluid fluid = rl != null && BuiltInRegistries.FLUID.containsKey(rl)
                ? BuiltInRegistries.FLUID.get(rl) : Fluids.WATER;
        return new FluidStack(fluid, content.amount());
    }
}
