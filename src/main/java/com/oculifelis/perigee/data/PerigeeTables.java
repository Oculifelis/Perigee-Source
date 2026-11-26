package com.oculifelis.perigee.data;

import com.fundamentum.data.DataTable;
import com.fundamentum.data.DataTables;
import com.fundamentum.recipeview.RecipeViews;
import com.oculifelis.perigee.Perigee;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class PerigeeTables {
    public static final DataTable<Tide> TIDES = DataTables.register(Perigee.id("tides"), "perigee/tides", Tide::fromJson)
        .merge(DataTable.Merge.DEEP)
        .synced()
        .onLoad(t -> RecipeViews.refresh());

    public static final DataTable<BasinConfig> BASIN = DataTables.register(Perigee.id("basin"), "perigee/basin", BasinConfig::fromJson)
        .merge(DataTable.Merge.DEEP)
        .synced();

    public static final DataTable<MoonTide> MOONS = DataTables.register(Perigee.id("moons"), "perigee/moons", MoonTide::fromJson)
        .merge(DataTable.Merge.DEEP)
        .synced()
        .onLoad(t -> RecipeViews.refresh());

    public static final DataTable<StandRecipe> STAND_RECIPES = DataTables.register(Perigee.id("stand_recipes"),
            "perigee/stand_recipes", StandRecipe::fromJson)
        .merge(DataTable.Merge.DEEP)
        .synced()
        .onLoad(t -> RecipeViews.refresh());

    public static final DataTable<LensEffect> LENS_EFFECTS = DataTables.register(Perigee.id("lens_effects"),
            "perigee/lens_effects", LensEffect::fromJson)
        .merge(DataTable.Merge.DEEP)
        .synced();

    private PerigeeTables() {}

    public static void init() {
        // Loads the class so the tables register during mod construction.
    }

    public static BasinConfig basin() {
        BasinConfig c = BASIN.get(Perigee.id("basin"));
        if (c != null) return c;
        for (BasinConfig any : BASIN.entries().values()) return any;
        return BasinConfig.DEFAULT;
    }

    /** All tides in a stable order (by first phase). */
    public static List<Tide> tides() {
        List<Tide> out = new ArrayList<>(TIDES.entries().values());
        out.sort(Comparator.comparingInt(t -> t.phases().isEmpty() ? 99 : t.phases().get(0)));
        return out;
    }

    public static Optional<Tide> tideForPhase(int phase) {
        for (Tide t : tides()) if (t.covers(phase)) return Optional.of(t);
        return Optional.empty();
    }

    public static Optional<Tide> tideNamed(String name) {
        for (Tide t : TIDES.entries().values()) if (t.name().equals(name)) return Optional.of(t);
        return Optional.empty();
    }

    public static Optional<MoonTide> moonTide(String moonId) {
        if (moonId == null || moonId.isEmpty()) return Optional.empty();
        for (MoonTide m : MOONS.entries().values()) if (m.moon().equals(moonId)) return Optional.of(m);
        return Optional.empty();
    }

    public static Optional<LensEffect> lensEffect(Identifier lens) {
        if (lens == null) return Optional.empty();
        for (LensEffect e : LENS_EFFECTS.entries().values()) if (e.lens().equals(lens)) return Optional.of(e);
        return Optional.empty();
    }

    public static List<StandRecipe> standRecipes() {
        return List.copyOf(STAND_RECIPES.entries().values());
    }
}
