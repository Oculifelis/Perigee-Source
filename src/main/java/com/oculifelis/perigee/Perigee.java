package com.oculifelis.perigee;

import com.oculifelis.perigee.compat.PerigeeRecipeViews;
import com.oculifelis.perigee.data.PerigeeTables;
import com.oculifelis.perigee.mechanic.MedallionHandler;
import com.oculifelis.perigee.registry.PerigeeBlockEntities;
import com.oculifelis.perigee.registry.PerigeeBlocks;
import com.oculifelis.perigee.registry.PerigeeComponents;
import com.oculifelis.perigee.registry.PerigeeItems;
import com.oculifelis.perigee.tide.MoonLog;
import com.oculifelis.perigee.tide.MoonHooks;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Perigee.MODID)
public final class Perigee {
    public static final String MODID = "perigee";

    public Perigee(IEventBus modBus, ModContainer container) {
        PerigeeTables.init();
        PerigeeComponents.register(modBus);
        PerigeeBlocks.register(modBus);
        PerigeeItems.register(modBus);
        PerigeeBlockEntities.register(modBus);

        MoonLog.init();
        MoonHooks.init();
        MedallionHandler.init();
        PerigeeRecipeViews.init();
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            com.oculifelis.perigee.client.PerigeeClient.init(modBus);
        }
    }

    /** The item with that id, or null when it is missing or air. */
    public static net.minecraft.world.item.Item item(Identifier id) {
        if (id == null) return null;
        net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).orElse(null);
        return item == null || item == net.minecraft.world.item.Items.AIR ? null : item;
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
