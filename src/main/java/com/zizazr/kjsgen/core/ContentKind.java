package com.zizazr.kjsgen.core;

/**
 * What a slot is currently filled with.
 */
public enum ContentKind {
    /** Slot is empty / wildcard. */
    EMPTY,
    /** A concrete item (optionally with components/NBT). */
    ITEM,
    /** An item tag, e.g. #minecraft:planks. */
    ITEM_TAG,
    /** A concrete fluid with an amount in mB. */
    FLUID,
    /** A fluid tag with an amount in mB. */
    FLUID_TAG,
    /** A Mekanism chemical (gas/infuse type/pigment/slurry) with an amount in mB. */
    CHEMICAL,
    /** A Mekanism chemical tag with an amount in mB. */
    CHEMICAL_TAG;

    public boolean isFluid() {
        return this == FLUID || this == FLUID_TAG;
    }

    public boolean isChemical() {
        return this == CHEMICAL || this == CHEMICAL_TAG;
    }

    /** Fluid or chemical: content measured in mB rather than a stack count. */
    public boolean hasAmount() {
        return isFluid() || isChemical();
    }

    public boolean isTag() {
        return this == ITEM_TAG || this == FLUID_TAG || this == CHEMICAL_TAG;
    }
}
