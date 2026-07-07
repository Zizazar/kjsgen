package com.zizazr.kjsgen.ui.vanilla;

/**
 * The per-operator colour palette shared by the collaborative cursor and head-icon overlays.
 * The server hands out a stable colour <em>index</em> per viewer; the client maps it here.
 */
final class CollabColors {
    private CollabColors() {
    }

    /** Distinct, high-contrast colours. */
    private static final int[] PALETTE = {
            0xFFE6194B, 0xFF3CB44B, 0xFFFFE119, 0xFF4363D8, 0xFFF58231,
            0xFF911EB4, 0xFF42D4F4, 0xFFF032E6, 0xFFBFEF45, 0xFFFA69B4,
    };

    static int colorFor(int index) {
        return PALETTE[Math.floorMod(index, PALETTE.length)];
    }
}
