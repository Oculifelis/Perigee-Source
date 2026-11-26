package com.oculifelis.perigee.registry;

import com.oculifelis.perigee.Perigee;
import com.oculifelis.perigee.world.TidalStandBlockEntity;
import com.oculifelis.perigee.world.TideBasinBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

public final class PerigeeBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Perigee.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TideBasinBlockEntity>> TIDE_BASIN =
        BLOCK_ENTITIES.register("tide_basin", () -> new BlockEntityType<>(TideBasinBlockEntity::new,
            Set.of(PerigeeBlocks.TIDE_BASIN.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TidalStandBlockEntity>> TIDAL_STAND =
        BLOCK_ENTITIES.register("tidal_stand", () -> new BlockEntityType<>(TidalStandBlockEntity::new,
            Set.of(PerigeeBlocks.TIDAL_STAND.get())));

    private PerigeeBlockEntities() {}

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
