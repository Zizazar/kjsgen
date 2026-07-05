package com.zizazr.kjsgen.ui;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Callbacks the live JEI layout-edit canvas uses to talk to the layout editor
 * without depending on its internals (and without the editor depending on JEI
 * classes). Coordinates are canvas-relative, slot boxes are 18x18.
 */
public interface LayoutEditHost {
    /** Keys of all slots currently present in the edited definition. */
    Set<String> slotKeys();

    int slotX(String key);

    int slotY(String key);

    /** Live position update while a slot is dragged. */
    void moveSlot(String key, int x, int y);

    /** A slot was clicked. */
    void selectSlot(String key);

    /** Drag finished: refresh the properties panel (x/y fields). */
    void dragEnded();

    /** Key of the selected slot for the highlight, or null. */
    @Nullable
    String selectedSlotKey();
}
