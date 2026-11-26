package com.oculifelis.perigee.item;

import com.oculifelis.perigee.registry.PerigeeComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** A ground moon-glass lens tuned to one tide. A fixed lens works at full strength on every phase. */
public class LensItem extends Item {
    public LensItem(Properties props) {
        super(props);
    }

    public static boolean isFixed(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(PerigeeComponents.FIXED.get()));
    }

    public static ItemStack fixed(ItemStack stack) {
        stack.set(PerigeeComponents.FIXED.get(), true);
        return stack;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return isFixed(stack) || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out,
                                TooltipFlag flag) {
        out.accept(Component.translatable(isFixed(stack) ? "tooltip.perigee.lens_fixed" : "tooltip.perigee.lens_tuned")
            .withStyle(isFixed(stack) ? ChatFormatting.GOLD : ChatFormatting.GRAY));
    }
}
