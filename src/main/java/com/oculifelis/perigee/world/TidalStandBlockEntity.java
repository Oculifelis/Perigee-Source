package com.oculifelis.perigee.world;

import com.oculifelis.perigee.Perigee;
import com.oculifelis.perigee.data.PerigeeTables;
import com.oculifelis.perigee.data.StandRecipe;
import com.oculifelis.perigee.item.LensItem;
import com.oculifelis.perigee.registry.PerigeeBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.Optional;

/** Holds one item that brine is poured over, counting pours toward a stand recipe. */
public class TidalStandBlockEntity extends BlockEntity {
    private ItemStack item = ItemStack.EMPTY;
    private int poured;
    private Identifier pouredLiquid;

    public TidalStandBlockEntity(BlockPos pos, BlockState state) {
        super(PerigeeBlockEntities.TIDAL_STAND.get(), pos, state);
    }

    public ItemStack item() {
        return item;
    }

    public int poured() {
        return poured;
    }

    public void setItem(ItemStack stack) {
        item = stack;
        poured = 0;
        pouredLiquid = null;
        sync();
    }

    /** The recipe that pouring {@code liquid} over {@code held} follows, if any. */
    public static Optional<StandRecipe> recipeFor(ItemStack held, ItemStack liquid) {
        if (held.isEmpty() || liquid.isEmpty()) return Optional.empty();
        Identifier in = BuiltInRegistries.ITEM.getKey(held.getItem());
        Identifier liq = BuiltInRegistries.ITEM.getKey(liquid.getItem());
        for (StandRecipe r : PerigeeTables.standRecipes()) {
            if (!r.input().equals(in) || !r.liquid().equals(liq)) continue;
            if (r.fixed() && LensItem.isFixed(held)) continue;
            return Optional.of(r);
        }
        return Optional.empty();
    }

    /** Pours one liquid; returns true when the recipe finished on this pour. */
    public boolean pour(StandRecipe recipe) {
        if (pouredLiquid == null || !pouredLiquid.equals(recipe.liquid())) {
            pouredLiquid = recipe.liquid();
            poured = 0;
        }
        poured++;
        if (poured < recipe.count()) {
            sync();
            return false;
        }
        ItemStack out;
        if (recipe.fixed()) {
            out = LensItem.fixed(item.copyWithCount(1));
        } else {
            Item made = Perigee.item(recipe.output());
            out = made == null ? item : new ItemStack(made);
        }
        item = out;
        poured = 0;
        pouredLiquid = null;
        sync();
        return true;
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide() && !item.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, item);
            item = ItemStack.EMPTY;
        }
        super.preRemoveSideEffects(pos, state);
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
        if (!item.isEmpty()) out.store("item", ItemStack.CODEC, item);
        out.putInt("poured", poured);
        if (pouredLiquid != null) out.putString("liquid", pouredLiquid.toString());
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        item = in.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY);
        poured = in.getIntOr("poured", 0);
        String liq = in.getStringOr("liquid", "");
        pouredLiquid = liq.isEmpty() ? null : Identifier.tryParse(liq);
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
