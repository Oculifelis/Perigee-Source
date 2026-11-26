package com.oculifelis.perigee.compat;

import com.fundamentum.recipeview.RecipeViews;
import com.fundamentum.recipeview.ViewCategory;
import com.oculifelis.perigee.Perigee;
import com.oculifelis.perigee.data.PerigeeTables;
import com.oculifelis.perigee.data.StandRecipe;
import com.oculifelis.perigee.item.LensItem;
import com.oculifelis.perigee.registry.PerigeeBlocks;
import com.oculifelis.perigee.registry.PerigeeItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Recipe viewer pages through Fundamentum, so JEI shows them with no JEI code here. */
public final class PerigeeRecipeViews {
    private PerigeeRecipeViews() {}

    public static void init() {
        for (var lens : PerigeeItems.lenses()) RecipeViews.subtypes(lens::get, s -> LensItem.isFixed(s) ? "fixed" : "loose");
        for (var lens : PerigeeItems.moonLenses()) RecipeViews.subtypes(lens::get, s -> LensItem.isFixed(s) ? "fixed" : "loose");
        RecipeViews.hide(stack -> !PerigeeItems.available(stack.getItem()));

        ViewCategory<StandRecipe> stand = RecipeViews.category("perigee:tidal_stand");
        int held = stand.input(14, 18);
        int liquid = stand.input(38, 18);
        int out = stand.output(96, 18);
        stand.title(Component.translatable("jei.perigee.tidal_stand"))
            .icon(() -> new ItemStack(PerigeeBlocks.TIDAL_STAND.get()))
            .size(136, 50)
            .arrow(64, 18, 40)
            .catalysts(() -> List.of(new ItemStack(PerigeeBlocks.TIDAL_STAND.get())))
            .recipes(PerigeeTables::standRecipes)
            .key(r -> r.input() + "+" + r.liquid() + (r.fixed() ? "#fixed" : ""))
            .fill((r, view) -> {
                ItemStack in = stackOf(r.input(), 1);
                ItemStack liq = stackOf(r.liquid(), r.count());
                if (in.isEmpty() || liq.isEmpty()) return;
                view.item(held, in);
                view.item(liquid, liq);
                view.item(out, r.fixed() ? LensItem.fixed(in.copy()) : stackOf(r.output(), 1));
                view.text(Component.translatable("jei.perigee.pours", r.count()), 40);
            });

        RecipeViews.info(() -> List.of(new ItemStack(PerigeeBlocks.TIDE_BASIN.get())),
            () -> List.of(Component.translatable("jei.perigee.info.basin")));
        RecipeViews.info(PerigeeRecipeViews::brineStacks,
            () -> List.of(Component.translatable("jei.perigee.info.brine")));
        RecipeViews.info(() -> List.of(new ItemStack(PerigeeItems.PHASE_MEDALLION.get())),
            () -> List.of(Component.translatable("jei.perigee.info.medallion")));
        RecipeViews.info(() -> List.of(new ItemStack(PerigeeBlocks.TIDE_DIAL.get())),
            () -> List.of(Component.translatable("jei.perigee.info.dial")));
    }

    private static List<ItemStack> brineStacks() {
        List<ItemStack> out = new ArrayList<>();
        for (var b : PerigeeItems.brines()) out.add(new ItemStack(b.get()));
        for (var b : PerigeeItems.moonBrines()) if (PerigeeItems.available(b.get())) out.add(new ItemStack(b.get()));
        return out;
    }

    private static ItemStack stackOf(Identifier id, int count) {
        Item item = Perigee.item(id);
        return item == null ? ItemStack.EMPTY : new ItemStack(item, count);
    }
}
