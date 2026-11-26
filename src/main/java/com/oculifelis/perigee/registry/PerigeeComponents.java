package com.oculifelis.perigee.registry;

import com.mojang.serialization.Codec;
import com.oculifelis.perigee.Perigee;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class PerigeeComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
        DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Perigee.MODID);

    /** The lens item socketed in a Phase Medallion. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Identifier>> LENS =
        COMPONENTS.registerComponentType("lens", builder -> builder
            .persistent(Identifier.CODEC)
            .networkSynchronized(Identifier.STREAM_CODEC));

    /** A lens fixed with salts works at full strength on every phase. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> FIXED =
        COMPONENTS.registerComponentType("fixed", builder -> builder
            .persistent(Codec.BOOL)
            .networkSynchronized(ByteBufCodecs.BOOL));

    /** The night a worn medallion last saw, written by the server so the client knows tonight's phase. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> NIGHT =
        COMPONENTS.registerComponentType("night", builder -> builder
            .networkSynchronized(ByteBufCodecs.VAR_LONG));

    private PerigeeComponents() {}

    public static void register(IEventBus bus) {
        COMPONENTS.register(bus);
    }
}
