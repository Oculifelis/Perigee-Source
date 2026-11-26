package com.oculifelis.perigee.world;

import com.fundamentum.moon.Moons;
import com.oculifelis.perigee.Perigee;
import com.oculifelis.perigee.data.BasinConfig;
import com.oculifelis.perigee.data.MoonTide;
import com.oculifelis.perigee.data.PerigeeTables;
import com.oculifelis.perigee.data.Tide;
import com.oculifelis.perigee.registry.PerigeeBlockEntities;
import com.oculifelis.perigee.tide.MoonLog;
import com.oculifelis.perigee.tide.TideMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;
import java.util.function.LongFunction;

/**
 * Holds only the fill time and how many bottles were taken. Everything else (condensing, which tide, drying into
 * salt) is worked out from the moon clock when the basin is used or looked at.
 */
public class TideBasinBlockEntity extends BlockEntity {
    public enum Stage { EMPTY, CONDENSING, READY, DRIED }

    /** Snapshot of a basin's state at one moment. */
    public record State(Stage stage, double progress, Identifier brine, Identifier salt, int bottlesLeft, int salts, String tide) {}

    private long filledAt = -1;
    private int taken;

    public TideBasinBlockEntity(BlockPos pos, BlockState state) {
        super(PerigeeBlockEntities.TIDE_BASIN.get(), pos, state);
    }

    public boolean isEmpty() {
        return filledAt < 0;
    }

    public long filledAt() {
        return filledAt;
    }

    public void fill(long now) {
        filledAt = now;
        taken = 0;
        sync();
    }

    public void empty() {
        filledAt = -1;
        taken = 0;
        sync();
    }

    public static LongFunction<Double> speedFor(ServerLevel level) {
        MoonLog log = MoonLog.get(level);
        return night -> PerigeeTables.moonTide(log.moonOn(night)).map(MoonTide::speed).orElse(1.0);
    }

    public State state(ServerLevel level) {
        return state(level, Moons.dayTime(level), speedFor(level), MoonLog.get(level)::moonOn);
    }

    /** The state at {@code now}, given the rate and the special moon for each night. Public for tests. */
    public State state(ServerLevel level, long now, LongFunction<Double> speed, LongFunction<String> moonOf) {
        BasinConfig cfg = PerigeeTables.basin();
        if (filledAt < 0) return new State(Stage.EMPTY, 0, null, null, 0, 0, "");
        if (now < filledAt) {
            // The clock was set back; start over from now.
            filledAt = now;
            setChanged();
        }
        long finish = TideMath.finishTime(filledAt, now, cfg.condenseTicks(), speed);
        if (finish < 0) {
            return new State(Stage.CONDENSING, TideMath.progress(filledAt, now, cfg.condenseTicks(), speed), null, null, 0, 0, "");
        }
        long night = TideMath.nightOf(finish);
        Optional<Tide> tide = PerigeeTables.tideForPhase(TideMath.phaseOfNight(night));
        Identifier brine = tide.map(Tide::brine).orElse(null);
        Identifier salt = tide.map(Tide::salt).orElse(null);
        String tideName = tide.map(Tide::name).orElse("");
        Optional<MoonTide> special = PerigeeTables.moonTide(moonOf.apply(night));
        if (special.isPresent() && special.get().brine() != null) {
            brine = special.get().brine();
            String path = brine.getPath();
            tideName = path.endsWith("_brine") ? path.substring(0, path.length() - 6) : path;
        }
        int left = Math.max(0, cfg.bottles() - taken);
        if (TideMath.sunTicks(finish, now) >= cfg.dryTicks()) {
            return new State(Stage.DRIED, 1.0, brine, salt, left, TideMath.saltsFor(cfg, left), tideName);
        }
        return new State(Stage.READY, 1.0, brine, salt, left, 0, tideName);
    }

    /** Takes one bottle of brine if ready; empty stack otherwise. */
    public ItemStack takeBottle(ServerLevel level) {
        State s = state(level);
        if (s.stage() != Stage.READY || s.brine() == null || s.bottlesLeft() <= 0) return ItemStack.EMPTY;
        Item item = Perigee.item((s.brine()));
        if (item == null) return ItemStack.EMPTY;
        taken++;
        if (taken >= PerigeeTables.basin().bottles()) {
            filledAt = -1;
            taken = 0;
        }
        sync();
        return new ItemStack(item);
    }

    /** Scrapes out the dried salt if there is any; empty stack otherwise. */
    public ItemStack takeSalt(ServerLevel level) {
        State s = state(level);
        if (s.stage() != Stage.DRIED) return ItemStack.EMPTY;
        ItemStack out = ItemStack.EMPTY;
        if (s.salt() != null && s.salts() > 0) {
            Item item = Perigee.item((s.salt()));
            if (item != null) out = new ItemStack(item, s.salts());
        }
        empty();
        return out;
    }

    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putLong("filled_at", filledAt);
        out.putInt("taken", taken);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        filledAt = in.getLongOr("filled_at", -1L);
        taken = in.getIntOr("taken", 0);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithFullMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
