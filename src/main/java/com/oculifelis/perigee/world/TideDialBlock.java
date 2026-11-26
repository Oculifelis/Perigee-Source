package com.oculifelis.perigee.world;

import com.fundamentum.moon.Moons;
import com.mojang.serialization.MapCodec;
import com.oculifelis.perigee.data.PerigeeTables;
import com.oculifelis.perigee.tide.MoonLog;
import com.oculifelis.perigee.tide.TideMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Gives a redstone signal for the tide: nothing by day, at night the basin pull of tonight's phase (strongest at full
 * and new moon), and full power on a special moon.
 */
public class TideDialBlock extends Block {
    public static final MapCodec<TideDialBlock> CODEC = simpleCodec(TideDialBlock::new);
    public static final IntegerProperty POWER = BlockStateProperties.POWER;

    public TideDialBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(POWER, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWER);
    }

    /** The signal at that moment. Public for tests. */
    public static int powerAt(ServerLevel level, long time) {
        if (!TideMath.nightTime(time)) return 0;
        long night = TideMath.nightOf(time);
        if (!MoonLog.moonOn(level, night).isEmpty()) return 15;
        return Math.max(0, Math.min(15, PerigeeTables.basin().pull(TideMath.phaseOfNight(night))));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (level instanceof ServerLevel server) server.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        long time = Moons.dayTime(level);
        int power = powerAt(level, time);
        if (power != state.getValue(POWER)) level.setBlock(pos, state.setValue(POWER, power), Block.UPDATE_ALL);
        long wait = Math.max(20L, Math.min(TideMath.ticksToBoundary(time), 1200L));
        level.scheduleTick(pos, this, (int) wait);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return state.getValue(POWER);
    }
}
