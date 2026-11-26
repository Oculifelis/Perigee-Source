package com.oculifelis.perigee.gametest;

import com.oculifelis.perigee.Perigee;
import com.oculifelis.perigee.data.PerigeeTables;
import com.oculifelis.perigee.data.StandRecipe;
import com.oculifelis.perigee.data.Tide;
import com.oculifelis.perigee.item.LensItem;
import com.oculifelis.perigee.item.PhaseMedallionItem;
import com.oculifelis.perigee.mechanic.MedallionHandler;
import com.oculifelis.perigee.registry.PerigeeBlocks;
import com.oculifelis.perigee.registry.PerigeeItems;
import com.oculifelis.perigee.tide.TideMath;
import com.oculifelis.perigee.world.TidalStandBlockEntity;
import com.oculifelis.perigee.world.TideBasinBlockEntity;
import com.oculifelis.perigee.world.TideDialBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Server GameTests: data tables, basin condensing and drying, stand pours, medallion strength, dial and recipes. */
@EventBusSubscriber(modid = Perigee.MODID)
public final class PerigeeGameTests {
    private static final Map<String, Test> TESTS = new LinkedHashMap<>();

    private record Test(Consumer<GameTestHelper> body, int maxTicks) {}

    static {
        TESTS.put("tables_loaded", new Test(PerigeeGameTests::tablesLoaded, 40));
        TESTS.put("phase_grouping", new Test(PerigeeGameTests::phaseGrouping, 40));
        TESTS.put("basin_condense", new Test(PerigeeGameTests::basinCondense, 40));
        TESTS.put("basin_special_moon", new Test(PerigeeGameTests::basinSpecialMoon, 40));
        TESTS.put("basin_dries_to_salt", new Test(PerigeeGameTests::basinDries, 40));
        TESTS.put("stand_makes_lens", new Test(PerigeeGameTests::standMakesLens, 40));
        TESTS.put("medallion_strength", new Test(PerigeeGameTests::medallionStrength, 40));
        TESTS.put("dial_signal", new Test(PerigeeGameTests::dialSignal, 40));
        TESTS.put("crafting_recipes", new Test(PerigeeGameTests::craftingRecipes, 40));
    }

    private PerigeeGameTests() {}

    @SubscribeEvent
    public static void registerFunctions(RegisterEvent event) {
        event.register(Registries.TEST_FUNCTION, helper ->
            TESTS.forEach((name, test) -> helper.register(Perigee.id(name), test.body())));
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        TESTS.forEach((name, test) -> {
            Holder<TestEnvironmentDefinition<?>> env =
                event.registerEnvironment(Perigee.id("env_" + name), new TestEnvironmentDefinition.AllOf(List.of()));
            event.registerTest(Perigee.id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, Perigee.id(name)),
                new TestData<>(env, Perigee.id("empty"), test.maxTicks(), 0, true)));
        });
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        if (!ok) helper.fail(message);
    }

    private static Item item(String path) {
        return Perigee.item(Perigee.id(path));
    }

    private static TideBasinBlockEntity basin(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PerigeeBlocks.TIDE_BASIN.get());
        if (!(helper.getBlockEntity(pos, TideBasinBlockEntity.class) instanceof TideBasinBlockEntity be)) {
            throw new IllegalStateException("no basin block entity");
        }
        return be;
    }

    // --- data ---

    private static void tablesLoaded(GameTestHelper helper) {
        check(helper, PerigeeTables.tides().size() == 4, "expected 4 tides, got " + PerigeeTables.tides().size());
        check(helper, PerigeeTables.basin().bottles() == 3, "basin bottles should be 3");
        check(helper, PerigeeTables.basin().condenseTicks() == 5000, "basin condense ticks should be 5000");
        check(helper, PerigeeTables.moonTide("perigee:perigee").map(m -> m.speed() == 2.0).orElse(false), "perigee moon speed 2");
        for (String m : List.of("fundamentum:psychedelic", "ostentum:enchanted", "ostentum:glimmer", "ostentum:harvest",
                "arsmoriendi:cruenta", "arsmoriendi:nox_plenae", "arsmoriendi:nox_crescentis", "arsmoriendi:nox_novae",
                "arsmoriendi:nox_decrescentis")) {
            check(helper, PerigeeTables.moonTide(m).isPresent(), "missing moon tide " + m);
        }
        check(helper, PerigeeTables.standRecipes().size() == 28, "expected 28 stand recipes, got " + PerigeeTables.standRecipes().size());
        for (StandRecipe r : PerigeeTables.standRecipes()) {
            check(helper, Perigee.item(r.input()) != null, "stand recipe input missing " + r.input());
            check(helper, Perigee.item(r.liquid()) != null, "stand recipe liquid missing " + r.liquid());
        }
        for (var lens : PerigeeItems.lenses()) {
            Identifier id = lens.getId();
            check(helper, PerigeeTables.lensEffect(id).isPresent(), "no lens effect for " + id);
        }
        for (var lens : PerigeeItems.moonLenses()) {
            Identifier id = lens.getId();
            check(helper, PerigeeTables.lensEffect(id).isPresent(), "no lens effect for " + id);
        }
        helper.succeed();
    }

    private static void phaseGrouping(GameTestHelper helper) {
        String[] want = {"full", "waning", "waning", "waning", "new", "waxing", "waxing", "waxing"};
        for (int p = 0; p < 8; p++) {
            String got = PerigeeTables.tideForPhase(p).map(Tide::name).orElse("none");
            check(helper, got.equals(want[p]), "phase " + p + ": expected " + want[p] + ", got " + got);
        }
        helper.succeed();
    }

    // --- basin ---

    private static void basinCondense(GameTestHelper helper) {
        TideBasinBlockEntity be = basin(helper);
        long night = 40;
        long fill = night * TideMath.DAY + TideMath.NIGHT_START;
        be.fill(fill);
        var half = be.state(helper.getLevel(), fill + 2500, n -> 1.0, n -> "");
        check(helper, half.stage() == TideBasinBlockEntity.Stage.CONDENSING, "should still condense at half, got " + half.stage());
        check(helper, Math.abs(half.progress() - 0.5) < 0.01, "progress at half should be 0.5, got " + half.progress());
        var done = be.state(helper.getLevel(), fill + 5000, n -> 1.0, n -> "");
        check(helper, done.stage() == TideBasinBlockEntity.Stage.READY, "should be ready, got " + done.stage());
        String tide = PerigeeTables.tideForPhase(TideMath.phaseOfNight(night)).map(Tide::name).orElse("none");
        check(helper, done.tide().equals(tide), "tide should be " + tide + ", got " + done.tide());
        check(helper, done.brine() != null && done.brine().getPath().equals(tide + "_brine"), "brine should match the tide");
        check(helper, done.bottlesLeft() == 3, "3 bottles expected");
        // Daytime does not condense: filled at noon, the night has not come yet.
        be.fill(night * TideMath.DAY + 6000);
        var day = be.state(helper.getLevel(), night * TideMath.DAY + 12000, n -> 1.0, n -> "");
        check(helper, day.stage() == TideBasinBlockEntity.Stage.CONDENSING && day.progress() == 0.0, "no condensing by day");
        helper.succeed();
    }

    private static void basinSpecialMoon(GameTestHelper helper) {
        TideBasinBlockEntity be = basin(helper);
        long fill = 8 * TideMath.DAY + TideMath.NIGHT_START;
        be.fill(fill);
        var s = be.state(helper.getLevel(), fill + 2500, n -> 2.0, n -> "perigee:perigee");
        check(helper, s.stage() == TideBasinBlockEntity.Stage.READY, "perigee moon should condense twice as fast, got " + s.stage());
        check(helper, s.brine() != null && s.brine().equals(Perigee.id("perigee_brine")), "should make perigee brine, got " + s.brine());
        check(helper, s.tide().equals("perigee"), "tide name should be perigee, got " + s.tide());
        helper.succeed();
    }

    private static void basinDries(GameTestHelper helper) {
        TideBasinBlockEntity be = basin(helper);
        long night = 12;
        long fill = night * TideMath.DAY + TideMath.NIGHT_START;
        be.fill(fill);
        long nextDayEnd = (night + 1) * TideMath.DAY + TideMath.DAY_END;
        var s = be.state(helper.getLevel(), nextDayEnd, n -> 1.0, n -> "");
        check(helper, s.stage() == TideBasinBlockEntity.Stage.DRIED, "brine should dry after a full day, got " + s.stage());
        check(helper, s.salts() == 2, "a full basin dries to 2 salts, got " + s.salts());
        check(helper, s.salt() != null && s.salt().getPath().endsWith("_salt"), "salt id expected");
        helper.succeed();
    }

    // --- stand ---

    private static void standMakesLens(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, PerigeeBlocks.TIDAL_STAND.get());
        TidalStandBlockEntity stand = helper.getBlockEntity(pos, TidalStandBlockEntity.class);
        stand.setItem(new ItemStack(PerigeeItems.LENS_BLANK.get()));
        ItemStack brine = new ItemStack(item("full_brine"));
        var recipe = TidalStandBlockEntity.recipeFor(stand.item(), brine);
        check(helper, recipe.isPresent(), "blank lens + full brine should have a recipe");
        for (int i = 0; i < 3; i++) check(helper, !stand.pour(recipe.get()), "should not finish before 4 pours");
        check(helper, stand.pour(recipe.get()), "4th pour should finish");
        check(helper, stand.item().is(item("full_lens")), "should make a full lens, got " + stand.item());
        check(helper, !LensItem.isFixed(stand.item()), "new lens is not fixed");
        var fix = TidalStandBlockEntity.recipeFor(stand.item(), brine);
        check(helper, fix.isPresent() && fix.get().fixed(), "lens + its brine should fix it");
        for (int i = 0; i < 7; i++) stand.pour(fix.get());
        check(helper, stand.pour(fix.get()), "8th pour should fix");
        check(helper, LensItem.isFixed(stand.item()), "lens should be fixed");
        check(helper, TidalStandBlockEntity.recipeFor(stand.item(), brine).isEmpty(), "a fixed lens takes no more brine");
        check(helper, TidalStandBlockEntity.recipeFor(stand.item(), new ItemStack(item("new_brine"))).isEmpty(),
            "a full lens takes no new brine");
        helper.succeed();
    }

    // --- medallion ---

    private static void medallionStrength(GameTestHelper helper) {
        check(helper, MedallionHandler.strengthFor("full", 0) == 1.0, "full lens at full moon is 1.0");
        check(helper, Math.abs(MedallionHandler.strengthFor("full", 1) - 0.66) < 0.01, "full lens one phase off is 0.66");
        check(helper, MedallionHandler.strengthFor("full", 4) == 0.0, "full lens at new moon is 0");
        check(helper, MedallionHandler.strengthFor("waxing", 6) == 1.0, "waxing lens on a waxing night is 1.0");

        ItemStack medallion = new ItemStack(PerigeeItems.PHASE_MEDALLION.get());
        PhaseMedallionItem.setLens(medallion, new ItemStack(item("full_lens")));
        long fullNight = -1, newNight = -1;
        for (long n = 0; n < 16 && (fullNight < 0 || newNight < 0); n++) {
            int p = TideMath.phaseOfNight(n);
            if (p == 0 && fullNight < 0) fullNight = n;
            if (p == 4 && newNight < 0) newNight = n;
        }
        var onFull = MedallionHandler.powersFor(medallion, fullNight);
        check(helper, onFull.size() == 1 && onFull.get(0).power().type().equals("water_walk") && onFull.get(0).strength() == 1.0,
            "full lens on a full night gives water walk at 1.0");
        check(helper, MedallionHandler.powersFor(medallion, newNight).isEmpty(), "full lens on a new night gives nothing");

        ItemStack fixedMedallion = new ItemStack(PerigeeItems.PHASE_MEDALLION.get());
        PhaseMedallionItem.setLens(fixedMedallion, LensItem.fixed(new ItemStack(item("full_lens"))));
        var fixedOnNew = MedallionHandler.powersFor(fixedMedallion, newNight);
        check(helper, fixedOnNew.size() == 1 && fixedOnNew.get(0).strength() == 1.0, "a fixed lens works every night");

        ItemStack perigee = new ItemStack(PerigeeItems.PHASE_MEDALLION.get());
        PhaseMedallionItem.setLens(perigee, new ItemStack(item("perigee_lens")));
        var all = MedallionHandler.powersFor(perigee, newNight);
        check(helper, all.size() == 4 && all.stream().allMatch(a -> a.strength() == 0.5), "perigee lens gives 4 powers at half");
        helper.succeed();
    }

    // --- dial ---

    private static void dialSignal(GameTestHelper helper) {
        check(helper, TideDialBlock.powerAt(helper.getLevel(), 6000) == 0, "no signal by day");
        for (long n = 0; n < 8; n++) {
            int p = TideMath.phaseOfNight(n);
            int got = TideDialBlock.powerAt(helper.getLevel(), n * TideMath.DAY + 18000);
            int want = PerigeeTables.basin().pull(p);
            if (got != 15) check(helper, got == want, "night " + n + " phase " + p + ": expected " + want + ", got " + got);
        }
        helper.setBlock(new BlockPos(1, 1, 1), PerigeeBlocks.TIDE_DIAL.get());
        helper.succeed();
    }

    // --- crafting ---

    private static void craftingRecipes(GameTestHelper helper) {
        var recipes = helper.getLevel().recipeAccess().getRecipes();
        var want = new java.util.TreeSet<>(java.util.List.of("lens_blank", "moon_glass", "moon_glass_bottle",
                "moon_glass_pane", "phase_medallion", "tidal_stand", "tide_basin", "tide_dial"));
        var got = new java.util.TreeSet<String>();
        for (var holder : recipes) {
            if (holder.id().identifier().getNamespace().equals(Perigee.MODID)) got.add(holder.id().identifier().getPath());
        }
        check(helper, got.equals(want), "expected recipes " + want + ", got " + got);
        helper.succeed();
    }
}
