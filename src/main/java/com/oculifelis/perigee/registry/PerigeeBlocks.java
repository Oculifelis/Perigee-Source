package com.oculifelis.perigee.registry;

import com.oculifelis.perigee.Perigee;
import com.oculifelis.perigee.world.TidalStandBlock;
import com.oculifelis.perigee.world.TideBasinBlock;
import com.oculifelis.perigee.world.TideDialBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class PerigeeBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, Perigee.MODID);
    private static final List<DeferredHolder<Item, BlockItem>> BLOCK_ITEMS = new ArrayList<>();

    public static final DeferredHolder<Block, TideBasinBlock> TIDE_BASIN = register("tide_basin", TideBasinBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F, 6.0F).sound(SoundType.STONE)
            .requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredHolder<Block, TidalStandBlock> TIDAL_STAND = register("tidal_stand", TidalStandBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F, 6.0F).sound(SoundType.STONE)
            .requiresCorrectToolForDrops().noOcclusion());

    public static final DeferredHolder<Block, TideDialBlock> TIDE_DIAL = register("tide_dial", TideDialBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(1.5F, 6.0F).sound(SoundType.STONE)
            .requiresCorrectToolForDrops());

    public static final DeferredHolder<Block, TransparentBlock> MOON_GLASS = register("moon_glass", TransparentBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.3F).sound(SoundType.GLASS)
            .lightLevel(s -> 6).noOcclusion().isValidSpawn((s, l, p, e) -> false)
            .isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));

    public static final DeferredHolder<Block, IronBarsBlock> MOON_GLASS_PANE = register("moon_glass_pane", IronBarsBlock::new,
        () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.3F).sound(SoundType.GLASS)
            .lightLevel(s -> 6).noOcclusion());

    private PerigeeBlocks() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
    }

    public static List<DeferredHolder<Item, BlockItem>> blockItems() {
        return Collections.unmodifiableList(BLOCK_ITEMS);
    }

    private static <B extends Block> DeferredHolder<Block, B> register(String name, Function<BlockBehaviour.Properties, B> factory,
                                                                     Supplier<BlockBehaviour.Properties> props) {
        Identifier id = Perigee.id(name);
        DeferredHolder<Block, B> block = BLOCKS.register(name, () -> factory.apply(
            props.get().setId(ResourceKey.create(Registries.BLOCK, id))));
        DeferredHolder<Item, BlockItem> item = PerigeeItems.ITEMS.register(name, () -> new BlockItem(block.get(),
            new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix()));
        BLOCK_ITEMS.add(item);
        return block;
    }
}
