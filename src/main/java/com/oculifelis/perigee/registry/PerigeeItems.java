package com.oculifelis.perigee.registry;

import com.fundamentum.guide.book.GuideBooks;
import com.fundamentum.port.FId;
import com.oculifelis.perigee.Perigee;
import com.oculifelis.perigee.item.LensItem;
import com.oculifelis.perigee.item.PhaseMedallionItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public final class PerigeeItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, Perigee.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Perigee.MODID);

    /** The four tides; the Perigee moon adds a fifth brine and lens of its own. */
    public static final List<String> TIDES = List.of("full", "waxing", "new", "waning");
    public static final List<String> SPECIAL = List.of("perigee");

    /** Special moons of other mods, each with its own brine and lens; hidden when that mod is absent. */
    public record OtherMoon(String name, String modid) {}

    public static final List<OtherMoon> OTHER_MOONS = List.of(
        new OtherMoon("psychedelic", "fundamentum"),
        new OtherMoon("enchanted", "ostentum"),
        new OtherMoon("glimmer", "ostentum"),
        new OtherMoon("harvest", "ostentum"),
        new OtherMoon("cruenta", "arsmoriendi"),
        new OtherMoon("nox_plenae", "arsmoriendi"),
        new OtherMoon("nox_crescentis", "arsmoriendi"),
        new OtherMoon("nox_novae", "arsmoriendi"),
        new OtherMoon("nox_decrescentis", "arsmoriendi"));

    /** Perigee's own empty bottle, blown from moon glass panes. Brines go back into it when used. */
    public static final DeferredHolder<Item, Item> MOON_GLASS_BOTTLE = register("moon_glass_bottle", Item::new,
        () -> new Item.Properties());

    private static final List<DeferredHolder<Item, Item>> MOON_BRINES = new ArrayList<>();
    private static final List<DeferredHolder<Item, LensItem>> MOON_LENSES = new ArrayList<>();

    private static final List<DeferredHolder<Item, Item>> BRINES = new ArrayList<>();
    private static final List<DeferredHolder<Item, Item>> SALTS = new ArrayList<>();
    private static final List<DeferredHolder<Item, LensItem>> LENSES = new ArrayList<>();

    static {
        for (String t : TIDES) {
            BRINES.add(register(t + "_brine", Item::new,
                () -> new Item.Properties().stacksTo(16).craftRemainder(MOON_GLASS_BOTTLE.get())));
            SALTS.add(register(t + "_salt", Item::new, () -> new Item.Properties()));
            LENSES.add(register(t + "_lens", LensItem::new, () -> new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));
        }
        for (String s : SPECIAL) {
            BRINES.add(register(s + "_brine", Item::new,
                () -> new Item.Properties().stacksTo(16).rarity(Rarity.RARE).craftRemainder(MOON_GLASS_BOTTLE.get())));
            LENSES.add(register(s + "_lens", LensItem::new, () -> new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
        }
        for (OtherMoon m : OTHER_MOONS) {
            MOON_BRINES.add(register(m.name() + "_brine", Item::new,
                () -> new Item.Properties().stacksTo(16).rarity(Rarity.RARE).craftRemainder(MOON_GLASS_BOTTLE.get())));
            MOON_LENSES.add(register(m.name() + "_lens", LensItem::new, () -> new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
        }
    }

    /** True when the mod that owns this moon item is loaded (always true for Perigee's own items). */
    public static boolean available(Item item) {
        Identifier id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        for (OtherMoon m : OTHER_MOONS) {
            if (id.getPath().equals(m.name() + "_brine") || id.getPath().equals(m.name() + "_lens")) {
                return net.neoforged.fml.ModList.get().isLoaded(m.modid());
            }
        }
        return true;
    }

    public static final DeferredHolder<Item, Item> LENS_BLANK = register("lens_blank", Item::new,
        () -> new Item.Properties().stacksTo(16));

    public static final DeferredHolder<Item, PhaseMedallionItem> PHASE_MEDALLION = register("phase_medallion",
        PhaseMedallionItem::new, () -> new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () ->
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.perigee"))
            .icon(() -> new ItemStack(PHASE_MEDALLION.get()))
            .displayItems((params, output) -> {
                output.accept(fieldbook());
                for (DeferredHolder<Item, BlockItem> b : PerigeeBlocks.blockItems()) output.accept(new ItemStack(b.get()));
                output.accept(new ItemStack(MOON_GLASS_BOTTLE.get()));
                for (DeferredHolder<Item, Item> b : BRINES) output.accept(new ItemStack(b.get()));
                for (DeferredHolder<Item, Item> s : SALTS) output.accept(new ItemStack(s.get()));
                output.accept(new ItemStack(LENS_BLANK.get()));
                for (DeferredHolder<Item, LensItem> l : LENSES) {
                    output.accept(new ItemStack(l.get()));
                    output.accept(LensItem.fixed(new ItemStack(l.get())));
                }
                for (int i = 0; i < OTHER_MOONS.size(); i++) {
                    if (!net.neoforged.fml.ModList.get().isLoaded(OTHER_MOONS.get(i).modid())) continue;
                    output.accept(new ItemStack(MOON_BRINES.get(i).get()));
                    output.accept(new ItemStack(MOON_LENSES.get(i).get()));
                    output.accept(LensItem.fixed(new ItemStack(MOON_LENSES.get(i).get())));
                }
                output.accept(new ItemStack(PHASE_MEDALLION.get()));
            })
            .build());

    private PerigeeItems() {}

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
        TABS.register(bus);
    }

    public static List<DeferredHolder<Item, LensItem>> lenses() {
        return Collections.unmodifiableList(LENSES);
    }

    public static List<DeferredHolder<Item, Item>> moonBrines() {
        return Collections.unmodifiableList(MOON_BRINES);
    }

    public static List<DeferredHolder<Item, LensItem>> moonLenses() {
        return Collections.unmodifiableList(MOON_LENSES);
    }

    public static List<DeferredHolder<Item, Item>> brines() {
        return Collections.unmodifiableList(BRINES);
    }

    public static List<DeferredHolder<Item, Item>> salts() {
        return Collections.unmodifiableList(SALTS);
    }

    public static ItemStack fieldbook() {
        return GuideBooks.stack(FId.of(Perigee.MODID, "fieldbook"));
    }

    private static <I extends Item> DeferredHolder<Item, I> register(String name, Function<Item.Properties, I> factory,
                                                                    Supplier<Item.Properties> props) {
        Identifier id = Perigee.id(name);
        return ITEMS.register(name, () -> factory.apply(props.get().setId(ResourceKey.create(Registries.ITEM, id))));
    }
}
