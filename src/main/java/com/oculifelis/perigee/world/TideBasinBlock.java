package com.oculifelis.perigee.world;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import com.oculifelis.perigee.data.PerigeeTables;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A shallow stone basin that condenses brine from water while the moon is up. */
public class TideBasinBlock extends BaseEntityBlock {
    public static final MapCodec<TideBasinBlock> CODEC = simpleCodec(TideBasinBlock::new);
    /** Foot, stem and an open bowl, matching the font model (tools/models.py). */
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(2.0, 0.0, 2.0, 14.0, 2.0, 14.0),
        Block.box(4.0, 2.0, 4.0, 12.0, 4.0, 12.0),
        Shapes.join(
            Block.box(1.0, 4.0, 1.0, 15.0, 10.0, 15.0),
            Block.box(3.0, 5.0, 3.0, 13.0, 10.0, 13.0),
            net.minecraft.world.phys.shapes.BooleanOp.ONLY_FIRST));

    public TideBasinBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TideBasinBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    /** True when a basin here can be filled: open sky above and a dimension the basin table allows. */
    public static boolean canCondense(Level level, BlockPos pos) {
        String dim = level.dimension().identifier().toString();
        return PerigeeTables.basin().dimensions().contains(dim) && level.canSeeSky(pos.above());
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TideBasinBlockEntity basin)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (stack.is(Items.WATER_BUCKET)) {
            if (!basin.isEmpty()) return InteractionResult.TRY_WITH_EMPTY_HAND;
            if (!canCondense(level, pos)) {
                if (!level.isClientSide()) player.sendOverlayMessage(Component.translatable("message.perigee.basin_no_sky"));
                return InteractionResult.SUCCESS;
            }
            if (level instanceof ServerLevel server) {
                basin.fill(com.fundamentum.moon.Moons.dayTime(server));
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(com.oculifelis.perigee.registry.PerigeeItems.MOON_GLASS_BOTTLE.get())) {
            if (level instanceof ServerLevel server) {
                ItemStack brine = basin.takeBottle(server);
                if (brine.isEmpty()) {
                    showState(server, basin, player);
                } else {
                    player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, brine));
                    level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TideBasinBlockEntity basin)) return InteractionResult.PASS;
        if (level instanceof ServerLevel server) {
            ItemStack salt = basin.takeSalt(server);
            if (salt.isEmpty()) {
                showState(server, basin, player);
            } else {
                if (!player.getInventory().add(salt)) player.drop(salt, false);
                level.playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 0.8F, 1.2F);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static void showState(ServerLevel level, TideBasinBlockEntity basin, Player player) {
        player.sendOverlayMessage(describe(basin.state(level)));
    }

    /** One line saying what a basin is doing, shared with Jade. */
    public static Component describe(TideBasinBlockEntity.State s) {
        return switch (s.stage()) {
            case EMPTY -> Component.translatable("message.perigee.basin_empty");
            case CONDENSING -> Component.translatable("message.perigee.basin_condensing", (int) Math.round(s.progress() * 100));
            case READY -> Component.translatable("message.perigee.basin_ready",
                Component.translatable("tide.perigee." + s.tide()), s.bottlesLeft());
            case DRIED -> Component.translatable("message.perigee.basin_dried", s.salts());
        };
    }
}
