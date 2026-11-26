package com.oculifelis.perigee.world;

import com.mojang.serialization.MapCodec;
import com.oculifelis.perigee.Perigee;
import com.oculifelis.perigee.data.StandRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Optional;

/** A small stand that holds one item while brine is poured over it. */
public class TidalStandBlock extends BaseEntityBlock {
    public static final MapCodec<TidalStandBlock> CODEC = simpleCodec(TidalStandBlock::new);
    private static final VoxelShape SHAPE = Block.box(3.0, 0.0, 3.0, 13.0, 12.0, 13.0);

    public TidalStandBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TidalStandBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty() || !(level.getBlockEntity(pos) instanceof TidalStandBlockEntity stand)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (stand.item().isEmpty()) {
            if (!level.isClientSide()) {
                stand.setItem(stack.copyWithCount(1));
                stack.consume(1, player);
                level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_PLACE, SoundSource.BLOCKS, 0.7F, 1.2F);
            }
            return InteractionResult.SUCCESS;
        }
        Optional<StandRecipe> recipe = TidalStandBlockEntity.recipeFor(stand.item(), stack);
        if (recipe.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (!level.isClientSide()) {
            StandRecipe r = recipe.get();
            boolean done = stand.pour(r);
            stack.consume(1, player);
            ItemStack back = stackOf(r.returns());
            if (!back.isEmpty() && !player.getInventory().add(back)) player.drop(back, false);
            level.playSound(null, pos, done ? SoundEvents.AMETHYST_BLOCK_CHIME : SoundEvents.BOTTLE_EMPTY,
                SoundSource.BLOCKS, 0.8F, done ? 1.3F : 1.0F);
            player.sendOverlayMessage(done
                ? Component.translatable("message.perigee.stand_done", stand.item().getHoverName())
                : Component.translatable("message.perigee.stand_progress", stand.poured(), r.count()));
        }
        return InteractionResult.SUCCESS;
    }

    private static ItemStack stackOf(Identifier id) {
        Item item = Perigee.item(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TidalStandBlockEntity stand) || stand.item().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack out = stand.item();
            stand.setItem(ItemStack.EMPTY);
            if (!player.getInventory().add(out)) player.drop(out, false);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.BLOCKS, 0.7F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
}
