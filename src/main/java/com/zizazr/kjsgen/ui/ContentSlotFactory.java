package com.zizazr.kjsgen.ui;

import com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.FluidSlot;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.zizazr.kjsgen.core.SlotContent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.List;

/**
 * Builds the 18x18 visual element for a slot's current content: real item
 * render for items, cycling items for tags, fluid render for fluids.
 */
public final class ContentSlotFactory {
    private ContentSlotFactory() {
    }

    /** Element rendering the given content, JEI-slot sized (18x18). */
    public static UIElement build(SlotContent content) {
        return switch (content.kind()) {
            case EMPTY -> new ItemSlot();
            case ITEM -> new ItemSlot().setItem(stackOf(content));
            case ITEM_TAG -> tagElement(content);
            case FLUID, FLUID_TAG -> fluidElement(content);
            // legacy LDLib2 UI has no chemical render; show a generic placeholder
            case CHEMICAL, CHEMICAL_TAG -> new ItemSlot().setItem(new ItemStack(Items.GLASS_BOTTLE));
        };
    }

    public static ItemStack stackOf(SlotContent content) {
        ResourceLocation rl = ResourceLocation.tryParse(content.id());
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return new ItemStack(Items.BARRIER);
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(rl), content.count());
    }

    /** All items of an item tag, or a barrier when the tag is unknown/empty. */
    public static List<ItemStack> tagStacks(String tagId) {
        ResourceLocation rl = ResourceLocation.tryParse(tagId);
        if (rl != null) {
            TagKey<net.minecraft.world.item.Item> tagKey = TagKey.create(Registries.ITEM, rl);
            var holders = BuiltInRegistries.ITEM.getTag(tagKey);
            if (holders.isPresent()) {
                List<ItemStack> stacks = holders.get().stream()
                        .map(holder -> new ItemStack(holder.value()))
                        .toList();
                if (!stacks.isEmpty()) {
                    return stacks;
                }
            }
        }
        return List.of(new ItemStack(Items.BARRIER));
    }

    private static UIElement tagElement(SlotContent content) {
        UIElement slot = new UIElement();
        slot.layout(layout -> layout.width(18).height(18));
        slot.style(style -> style.backgroundTexture(ItemSlot.ITEM_SLOT_TEXTURE));
        UIElement icon = new UIElement();
        icon.layout(layout -> layout.width(16).height(16).marginAll(1));
        icon.style(style -> style.background(new ItemStackTexture(tagStacks(content.id()).toArray(ItemStack[]::new))));
        slot.addChild(icon);
        return slot;
    }

    private static UIElement fluidElement(SlotContent content) {
        FluidSlot slot = new FluidSlot();
        if (content.kind() == com.zizazr.kjsgen.core.ContentKind.FLUID) {
            ResourceLocation rl = ResourceLocation.tryParse(content.id());
            Fluid fluid = rl != null && BuiltInRegistries.FLUID.containsKey(rl)
                    ? BuiltInRegistries.FLUID.get(rl) : Fluids.WATER;
            slot.setFluid(new FluidStack(fluid, content.amount()));
        } else {
            // fluid tag: preview with water, the tooltip explains the tag
            slot.setFluid(new FluidStack(Fluids.WATER, content.amount()));
        }
        return slot;
    }
}
