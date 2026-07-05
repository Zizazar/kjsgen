package com.zizazr.kjsgen.integration.jei;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.zizazr.kjsgen.ui.LayoutEditHost;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.renderer.Rect2i;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The layout editor's canvas in "live JEI" mode: renders the mod's real
 * category panel (machine model, arrows, custom text) and lets the user drag
 * the <b>actual JEI slots</b> — positions are written back through
 * {@link IRecipeSlotDrawable#setPosition}, so the panel updates in place while
 * dragging. Slots deleted from the definition are parked far off-canvas
 * (JEI layouts cannot remove slots); slots added by the user are rendered by
 * the dialog as overlay elements on top of this canvas.
 */
public class JeiLayoutEditCanvas extends UIElement {
    /** Where deleted slots are parked, comfortably outside any canvas. */
    private static final int OFFSCREEN = -10000;

    private final IRecipeLayoutDrawable<?> layout;
    /** Native JEI slots keyed like the importer keys slot definitions. */
    private final Map<String, IRecipeSlotDrawable> slotsByKey;
    /** Per-slot offset between the ingredient rect and its background area. */
    private final Map<String, int[]> rectOffsets;
    private final LayoutEditHost host;

    private String draggedKey;
    private float grabDX, grabDY;
    private long lastTick = 0L;

    private JeiLayoutEditCanvas(IRecipeLayoutDrawable<?> layout,
                                Map<String, IRecipeSlotDrawable> slotsByKey,
                                Map<String, int[]> rectOffsets,
                                int canvasWidth, int canvasHeight,
                                LayoutEditHost host) {
        this.layout = layout;
        this.slotsByKey = slotsByKey;
        this.rectOffsets = rectOffsets;
        this.host = host;

        getLayout().width(canvasWidth).height(canvasHeight);
        addEventListener(UIEvents.MOUSE_DOWN, this::onMouseDown);
        addEventListener(UIEvents.MOUSE_MOVE, this::onMouseMove);
        addEventListener(UIEvents.MOUSE_UP, event -> endDrag());
        addEventListener(UIEvents.MOUSE_LEAVE, event -> endDrag());
    }

    /** Keys of the slots that exist natively in the JEI layout. */
    public Set<String> nativeKeys() {
        return slotsByKey.keySet();
    }

    @SuppressWarnings("removal")
    public static Optional<JeiLayoutEditCanvas> tryCreate(String typeId, int canvasWidth, int canvasHeight,
                                                          LayoutEditHost host) {
        IJeiRuntime runtime = KjsgenJeiPlugin.runtime();
        if (runtime == null) {
            return Optional.empty();
        }
        IRecipeManager recipeManager = runtime.getRecipeManager();
        IRecipeCategory<?> category = recipeManager.createRecipeCategoryLookup().get()
                .filter(c -> c.getRecipeType().getUid().toString().equals(typeId))
                .findFirst().orElse(null);
        if (category == null) {
            return Optional.empty();
        }
        IFocusGroup emptyFocus = runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup();
        Optional<? extends IRecipeLayoutDrawable<?>> layout = createLayout(recipeManager, category, emptyFocus);
        if (layout.isEmpty()) {
            return Optional.empty();
        }

        Map<String, IRecipeSlotDrawable> slotsByKey = new LinkedHashMap<>();
        Map<String, int[]> rectOffsets = new LinkedHashMap<>();
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
            Rect2i rect = drawable.getRect();
            Rect2i area = drawable.getAreaIncludingBackground();
            slotsByKey.put(key, drawable);
            rectOffsets.put(key, new int[]{rect.getX() - area.getX(), rect.getY() - area.getY()});
        }
        if (slotsByKey.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new JeiLayoutEditCanvas(layout.get(), slotsByKey, rectOffsets,
                canvasWidth, canvasHeight, host));
    }

    private static <T> Optional<IRecipeLayoutDrawable<T>> createLayout(IRecipeManager recipeManager,
                                                                       IRecipeCategory<T> category,
                                                                       IFocusGroup emptyFocus) {
        return recipeManager.createRecipeLookup(category.getRecipeType()).get().findFirst()
                .flatMap(sample -> recipeManager.createRecipeLayoutDrawable(category, sample, emptyFocus));
    }

    // ------------------------------------------------------------------ dragging

    private void onMouseDown(UIEvent event) {
        Optional<String> key = slotKeyAt(event.x, event.y);
        if (key.isEmpty()) {
            return; // background click bubbles up to the workbench (deselect)
        }
        event.stopPropagation();
        String slotKey = key.get();
        host.selectSlot(slotKey);
        draggedKey = slotKey;
        grabDX = event.x - (getContentX() + host.slotX(slotKey));
        grabDY = event.y - (getContentY() + host.slotY(slotKey));
    }

    private void onMouseMove(UIEvent event) {
        if (draggedKey == null || !host.slotKeys().contains(draggedKey)) {
            return;
        }
        int width = (int) getContentWidth();
        int height = (int) getContentHeight();
        int newX = clamp(Math.round(event.x - grabDX - getContentX()), width - 18);
        int newY = clamp(Math.round(event.y - grabDY - getContentY()), height - 18);
        host.moveSlot(draggedKey, newX, newY);
    }

    private void endDrag() {
        if (draggedKey != null) {
            draggedKey = null;
            host.dragEnded();
        }
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(value, max));
    }

    private Optional<String> slotKeyAt(double mouseX, double mouseY) {
        return layout.getRecipeSlotUnderMouse(mouseX, mouseY)
                .flatMap(slot -> slotsByKey.entrySet().stream()
                        .filter(entry -> entry.getValue() == slot)
                        .map(Map.Entry::getKey)
                        .findFirst());
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);

        long now = System.currentTimeMillis();
        if (now - lastTick >= 50L) {
            layout.tick();
            lastTick = now;
        }

        // the definition is authoritative: push its positions into the JEI slots
        Set<String> keys = host.slotKeys();
        slotsByKey.forEach((key, slot) -> {
            int[] offset = rectOffsets.get(key);
            if (keys.contains(key)) {
                slot.setPosition(host.slotX(key) + offset[0], host.slotY(key) + offset[1]);
            } else {
                slot.setPosition(OFFSCREEN, OFFSCREEN); // deleted from the definition
            }
        });

        int x = (int) getContentX();
        int y = (int) getContentY();
        layout.setPosition(x, y);
        layout.drawRecipe(guiContext.graphics, guiContext.mouseX, guiContext.mouseY);
        layout.drawOverlays(guiContext.graphics, guiContext.mouseX, guiContext.mouseY);

        // selection highlight on the JEI slot itself
        String selected = host.selectedSlotKey();
        if (selected != null && slotsByKey.containsKey(selected) && keys.contains(selected)) {
            var pose = guiContext.graphics.pose();
            pose.pushPose();
            pose.translate(x, y, 0);
            slotsByKey.get(selected).drawHighlight(guiContext.graphics, 0x8055FF55);
            pose.popPose();
        }
    }
}
