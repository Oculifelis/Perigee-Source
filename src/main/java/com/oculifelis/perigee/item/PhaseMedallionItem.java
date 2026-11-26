package com.oculifelis.perigee.item;

import com.oculifelis.perigee.Perigee;
import com.oculifelis.perigee.registry.PerigeeComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * A necklace (Curios) holding one lens. Use it with a lens in the other hand to set the lens; sneak-use with an
 * empty other hand to take the lens out.
 */
public class PhaseMedallionItem extends Item {
    public PhaseMedallionItem(Properties props) {
        super(props);
    }

    public static Identifier lens(ItemStack medallion) {
        return medallion.get(PerigeeComponents.LENS.get());
    }

    public static boolean fixed(ItemStack medallion) {
        return Boolean.TRUE.equals(medallion.get(PerigeeComponents.FIXED.get()));
    }

    /** The lens held, as a stack, or empty. */
    public static ItemStack lensStack(ItemStack medallion) {
        Identifier id = lens(medallion);
        if (id == null) return ItemStack.EMPTY;
        Item item = Perigee.item((id));
        if (item == null) return ItemStack.EMPTY;
        ItemStack out = new ItemStack(item);
        if (fixed(medallion)) LensItem.fixed(out);
        return out;
    }

    public static void setLens(ItemStack medallion, ItemStack lens) {
        if (lens.isEmpty()) {
            medallion.remove(PerigeeComponents.LENS.get());
            medallion.remove(PerigeeComponents.FIXED.get());
            return;
        }
        medallion.set(PerigeeComponents.LENS.get(), BuiltInRegistries.ITEM.getKey(lens.getItem()));
        if (LensItem.isFixed(lens)) medallion.set(PerigeeComponents.FIXED.get(), true);
        else medallion.remove(PerigeeComponents.FIXED.get());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack medallion = player.getItemInHand(hand);
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack other = player.getItemInHand(otherHand);
        if (other.getItem() instanceof LensItem) {
            if (!level.isClientSide()) {
                ItemStack old = lensStack(medallion);
                setLens(medallion, other);
                other.consume(1, player);
                if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
                player.sendOverlayMessage(Component.translatable("message.perigee.lens_set", lensStack(medallion).getHoverName()));
            }
            return InteractionResult.SUCCESS;
        }
        if (other.isEmpty() && player.isShiftKeyDown() && lens(medallion) != null) {
            if (!level.isClientSide()) {
                ItemStack old = lensStack(medallion);
                setLens(medallion, ItemStack.EMPTY);
                if (!old.isEmpty() && !player.getInventory().add(old)) player.drop(old, false);
            }
            return InteractionResult.SUCCESS;
        }
        return super.use(level, player, hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> out,
                                TooltipFlag flag) {
        ItemStack lens = lensStack(stack);
        if (lens.isEmpty()) {
            out.accept(Component.translatable("tooltip.perigee.medallion_empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        out.accept(Component.translatable("tooltip.perigee.medallion_lens", lens.getHoverName()).withStyle(ChatFormatting.AQUA));
        if (fixed(stack)) out.accept(Component.translatable("tooltip.perigee.lens_fixed").withStyle(ChatFormatting.GOLD));
    }
}
