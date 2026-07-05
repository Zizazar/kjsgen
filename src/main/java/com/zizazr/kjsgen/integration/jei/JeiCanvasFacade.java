package com.zizazr.kjsgen.integration.jei;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.zizazr.kjsgen.core.RecipeInstance;
import com.zizazr.kjsgen.core.RecipeTypeDefinition;
import com.zizazr.kjsgen.ui.KjsGenUI;
import com.zizazr.kjsgen.ui.LayoutEditHost;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Thin, JEI-type-free entry point for the live recipe canvas. Callers must
 * still guard every use with {@link com.zizazr.kjsgen.KjsGen#isJeiLoaded()};
 * the JVM only classloads this facade (and the JEI classes it pulls in) when
 * one of these methods is actually invoked, so packs without JEI are safe.
 */
public final class JeiCanvasFacade {
    private JeiCanvasFacade() {
    }

    /** Whether the JEI runtime is up (world loaded with JEI installed). */
    public static boolean runtimeAvailable() {
        return KjsgenJeiPlugin.runtime() != null;
    }

    /**
     * Builds a live JEI-rendered canvas for the recipe, or {@code null} when the
     * type is not a JEI category / has no sample recipe (caller falls back to
     * the static slot canvas).
     */
    @Nullable
    public static UIElement tryCreateCanvas(KjsGenUI ui, RecipeInstance recipe,
                                            RecipeTypeDefinition type, Runnable onChanged) {
        return JeiCanvasElement.tryCreate(ui, recipe, type, onChanged).orElse(null);
    }

    /** Called on every model refresh: updates error highlights and re-applies slot contents. */
    public static void updateValidation(UIElement canvas, java.util.Set<String> invalidSlotKeys) {
        if (canvas instanceof JeiCanvasElement element) {
            element.setInvalidKeys(invalidSlotKeys);
            element.markContentsDirty();
        }
    }

    /** Live-JEI canvas for the layout editor, or null (element + keys of the native JEI slots). */
    public record EditCanvas(UIElement element, Set<String> nativeKeys) {
    }

    @Nullable
    public static EditCanvas tryCreateEditCanvas(String typeId, int canvasWidth, int canvasHeight,
                                                 LayoutEditHost host) {
        return JeiLayoutEditCanvas.tryCreate(typeId, canvasWidth, canvasHeight, host)
                .map(canvas -> new EditCanvas(canvas, canvas.nativeKeys()))
                .orElse(null);
    }
}
