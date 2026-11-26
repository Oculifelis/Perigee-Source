package com.oculifelis.perigee.mechanic;

import com.fundamentum.moon.Moons;
import com.fundamentum.slots.Slots;
import com.oculifelis.perigee.Perigee;
import com.oculifelis.perigee.data.LensEffect;
import com.oculifelis.perigee.data.PerigeeTables;
import com.oculifelis.perigee.data.Tide;
import com.oculifelis.perigee.item.PhaseMedallionItem;
import com.oculifelis.perigee.registry.PerigeeComponents;
import com.oculifelis.perigee.tide.TideMath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs the powers of the lens in a worn Phase Medallion. Each lens names its powers in data
 * ({@code perigee/lens_effects}); their strength follows how close tonight's phase is to the lens's tide.
 */
public final class MedallionHandler {
    /** A power and how strong it is right now (0..1, times the power's own scale). */
    public record Active(LensEffect.Power power, double strength) {}

    private static final Map<UUID, List<Active>> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<UUID, Float> EBB_OWED = new HashMap<>();
    private static final Map<UUID, Float> EBB_RATE = new HashMap<>();
    private static final Set<UUID> PAYING = new HashSet<>();
    private static final Map<UUID, Map<Identifier, Holder<Attribute>>> MODIFIERS = new HashMap<>();

    private MedallionHandler() {}

    public static void init() {
        NeoForge.EVENT_BUS.addListener(MedallionHandler::onTick);
        NeoForge.EVENT_BUS.addListener(MedallionHandler::onVisibility);
        NeoForge.EVENT_BUS.addListener(MedallionHandler::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(MedallionHandler::onDamagePost);
        NeoForge.EVENT_BUS.addListener(MedallionHandler::onPickupXp);
        NeoForge.EVENT_BUS.addListener(MedallionHandler::onExperienceDrop);
        NeoForge.EVENT_BUS.addListener(MedallionHandler::onLogout);
    }

    // ---- working out the powers -------------------------------------------------------------------------------

    /** The worn medallion, or empty. */
    public static ItemStack worn(LivingEntity entity) {
        return Slots.firstMatching(entity, s -> s.getItem() instanceof PhaseMedallionItem)
            .map(Slots.Worn::stack).orElse(ItemStack.EMPTY);
    }

    /** How strongly a lens of {@code tideName} works on {@code phase}: the basin falloff by phase distance. */
    public static double strengthFor(String tideName, int phase) {
        Optional<Tide> tide = PerigeeTables.tideNamed(tideName);
        if (tide.isEmpty() || tide.get().phases().isEmpty()) return 0.0;
        int best = 8;
        for (int p : tide.get().phases()) best = Math.min(best, TideMath.phaseDistance(phase, p));
        return PerigeeTables.basin().falloffAt(best);
    }

    /** The powers a medallion gives on the night with that index. Public for tests. */
    public static List<Active> powersFor(ItemStack medallion, long night) {
        List<Active> out = new ArrayList<>();
        if (medallion.isEmpty()) return out;
        Identifier lens = PhaseMedallionItem.lens(medallion);
        Optional<LensEffect> effect = PerigeeTables.lensEffect(lens);
        if (effect.isEmpty()) return out;
        LensEffect e = effect.get();
        int phase = TideMath.phaseOfNight(night);
        double base = PhaseMedallionItem.fixed(medallion) || e.always() ? 1.0 : strengthFor(e.tide(), phase);
        for (LensEffect.Power p : e.powers()) {
            if (p.type().equals("tide_copy")) {
                // Borrows one tide lens's powers at full strength; which one changes every night.
                List<Tide> tides = PerigeeTables.tides();
                if (tides.isEmpty()) continue;
                Tide pick = tides.get((int) Math.floorMod(night * 31L + 7L, (long) tides.size()));
                PerigeeTables.lensEffect(pick.lens()).ifPresent(copy -> {
                    for (LensEffect.Power cp : copy.powers()) {
                        if (!cp.type().equals("tide_copy")) out.add(new Active(cp, base * p.scale() * cp.scale()));
                    }
                });
                continue;
            }
            double s = base * p.scale();
            if (s > 0.0) out.add(new Active(p, s));
        }
        return out;
    }

    /** The tide copied on that night by a psychedelic lens, for tooltips and Jade. */
    public static Optional<Tide> copiedTide(long night) {
        List<Tide> tides = PerigeeTables.tides();
        if (tides.isEmpty()) return Optional.empty();
        return Optional.of(tides.get((int) Math.floorMod(night * 31L + 7L, (long) tides.size())));
    }

    private static double strongest(List<Active> powers, String type) {
        double best = 0.0;
        for (Active a : powers) if (a.power().type().equals(type)) best = Math.max(best, a.strength());
        return best;
    }

    private static Optional<Active> first(List<Active> powers, String type) {
        Active best = null;
        for (Active a : powers) {
            if (a.power().type().equals(type) && (best == null || a.strength() > best.strength())) best = a;
        }
        return Optional.ofNullable(best);
    }

    private static List<Active> active(Player player) {
        return ACTIVE.getOrDefault(player.getUUID(), List.of());
    }

    // ---- ticking ----------------------------------------------------------------------------------------------

    private static void onTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
            serverTick(server, sp);
        } else if (level.isClientSide()) {
            ItemStack m = worn(player);
            Long night = m.isEmpty() ? null : m.get(PerigeeComponents.NIGHT.get());
            if (night != null) {
                double walk = strongest(powersFor(m, night), "water_walk");
                if (walk > 0.0) waterWalk(player);
            }
        }
    }

    private static void serverTick(ServerLevel level, ServerPlayer player) {
        UUID id = player.getUUID();
        int age = player.tickCount;
        if (age % 5 == 0) {
            ItemStack m = worn(player);
            if (m.isEmpty()) {
                ACTIVE.remove(id);
            } else {
                long night = TideMath.nightOf(Moons.dayTime(level));
                Long old = m.get(PerigeeComponents.NIGHT.get());
                if (old == null || old != night) m.set(PerigeeComponents.NIGHT.get(), night);
                ACTIVE.put(id, List.copyOf(powersFor(m, night)));
            }
        }
        List<Active> powers = active(player);
        if (strongest(powers, "water_walk") > 0.0) waterWalk(player);
        if (age % 5 == 0) first(powers, "pull").ifPresent(a -> pull(level, player, a));
        if (age % 2 == 0) first(powers, "xp_pull").ifPresent(a -> xpPull(level, player, a));
        if (age % 20 == 0) {
            for (Active a : powers) {
                switch (a.power().type()) {
                    case "effect" -> effect(player, a);
                    case "glow" -> glow(level, player, a);
                    case "grow" -> grow(level, player, a);
                    default -> { }
                }
            }
            attributes(player, powers);
            payEbb(level, player);
        }
    }

    private static void waterWalk(Player player) {
        if (player.isShiftKeyDown() || player.isSpectator() || player.getAbilities().flying || player.isInWater()) return;
        Level level = player.level();
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
        FluidState fluid = level.getFluidState(below);
        if (!fluid.is(FluidTags.WATER) || !level.getFluidState(below.above()).isEmpty()) return;
        double surface = below.getY() + fluid.getHeight(level, below);
        Vec3 v = player.getDeltaMovement();
        if (v.y > 0.0 || player.getY() > surface + 0.05 || player.getY() < surface - 0.4) return;
        player.setPos(player.getX(), surface, player.getZ());
        player.setDeltaMovement(v.x, 0.0, v.z);
        player.setOnGround(true);
        player.resetFallDistance();
    }

    private static void pull(ServerLevel level, ServerPlayer player, Active a) {
        double radius = a.power().num("radius", 8.0) * Math.min(1.0, a.strength());
        if (radius < 1.0) return;
        double speed = a.power().num("speed", 0.25);
        AABB box = player.getBoundingBox().inflate(radius);
        Vec3 to = player.position().add(0.0, 0.5, 0.0);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.hasPickUpDelay())) {
            item.setDeltaMovement(to.subtract(item.position()).normalize().scale(speed));
        }
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, box, ExperienceOrb::isAlive)) {
            orb.setDeltaMovement(to.subtract(orb.position()).normalize().scale(speed));
        }
    }

    /**
     * Draws every XP orb in reach to the wearer. The reach follows the moon in the sky: widest at full moon,
     * narrowest at new moon ({@code radius_full}, {@code radius_new}).
     */
    private static void xpPull(ServerLevel level, ServerPlayer player, Active a) {
        double radius = xpPullRadius(a.power(), Moons.moonPhase(Moons.dayTime(level))) * Math.min(1.0, a.strength());
        if (radius < 1.0) return;
        double speed = a.power().num("speed", 0.6);
        Vec3 to = player.position().add(0.0, 0.5, 0.0);
        for (ExperienceOrb orb : level.getEntitiesOfClass(ExperienceOrb.class, player.getBoundingBox().inflate(radius),
                o -> o.isAlive() && o.distanceToSqr(to) <= radius * radius)) {
            Vec3 d = to.subtract(orb.position());
            orb.setDeltaMovement(d.length() < 1.5 ? d : d.normalize().scale(speed));
        }
    }

    /** The reach of an {@code xp_pull} power on a moon phase (0 = full). Public for tests. */
    public static double xpPullRadius(LensEffect.Power power, int phase) {
        double light = 1.0 - TideMath.phaseDistance(phase, 0) / 4.0;
        double lo = power.num("radius_new", 8.0);
        double hi = power.num("radius_full", 32.0);
        return lo + (hi - lo) * light;
    }

    private static void effect(ServerPlayer player, Active a) {
        Identifier id = Identifier.tryParse(a.power().str("effect", ""));
        if (id == null) return;
        Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(id);
        if (holder.isEmpty()) return;
        int amp = (int) a.power().num("amplifier", 0.0);
        player.addEffect(new MobEffectInstance(holder.get(), 220, amp, true, false, true));
    }

    private static void glow(ServerLevel level, ServerPlayer player, Active a) {
        double radius = a.power().num("radius", 16.0) * Math.min(1.0, a.strength());
        if (radius < 1.0) return;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e instanceof Enemy && e.isAlive())) {
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, true, false, false));
        }
    }

    private static void grow(ServerLevel level, ServerPlayer player, Active a) {
        int radius = (int) a.power().num("radius", 6.0);
        int tries = (int) Math.round(a.power().num("tries", 8.0) * a.strength());
        RandomSource random = level.getRandom();
        BlockPos at = player.blockPosition();
        for (int i = 0; i < tries; i++) {
            BlockPos pos = at.offset(random.nextInt(radius * 2 + 1) - radius, random.nextInt(5) - 2,
                random.nextInt(radius * 2 + 1) - radius);
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.CROPS) && state.isRandomlyTicking()) state.randomTick(level, pos, random);
        }
    }

    private static void attributes(ServerPlayer player, List<Active> powers) {
        Map<Identifier, Holder<Attribute>> had = MODIFIERS.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
        Map<Identifier, Holder<Attribute>> now = new HashMap<>();
        int n = 0;
        for (Active a : powers) {
            if (!a.power().type().equals("attribute")) continue;
            Identifier attrId = Identifier.tryParse(a.power().str("attribute", ""));
            if (attrId == null) continue;
            Optional<Holder.Reference<Attribute>> holder = BuiltInRegistries.ATTRIBUTE.get(attrId);
            if (holder.isEmpty()) continue;
            AttributeInstance inst = player.getAttribute(holder.get());
            if (inst == null) continue;
            Identifier modId = Perigee.id("lens/" + n++);
            inst.addOrUpdateTransientModifier(new AttributeModifier(modId, a.power().num("amount", 0.0) * a.strength(),
                operation(a.power().str("operation", "add_multiplied_base"))));
            now.put(modId, holder.get());
        }
        for (Map.Entry<Identifier, Holder<Attribute>> old : had.entrySet()) {
            if (now.containsKey(old.getKey()) && now.get(old.getKey()).equals(old.getValue())) continue;
            AttributeInstance inst = player.getAttribute(old.getValue());
            if (inst != null) inst.removeModifier(old.getKey());
        }
        had.clear();
        had.putAll(now);
    }

    private static AttributeModifier.Operation operation(String name) {
        for (AttributeModifier.Operation op : AttributeModifier.Operation.values()) {
            if (op.getSerializedName().equals(name)) return op;
        }
        return AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
    }

    // ---- events -----------------------------------------------------------------------------------------------

    private static void onVisibility(LivingEvent.LivingVisibilityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getLookingEntity() instanceof Enemy)) return;
        first(active(player), "shadow_hide").ifPresent(a -> {
            int light = player.level().getMaxLocalRawBrightness(player.blockPosition());
            if (light <= a.power().num("light_max", 7.0)) {
                event.modifyVisibility(Math.max(0.05, 1.0 - a.power().num("hide", 0.6) * Math.min(1.0, a.strength())));
            }
        });
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        UUID id = player.getUUID();
        if (PAYING.contains(id) || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        first(active(player), "ebb").ifPresent(a -> {
            float share = (float) Math.min(0.9, a.power().num("share", 0.3) * a.strength());
            float held = event.getAmount() * share;
            if (held <= 0.0F) return;
            event.setAmount(event.getAmount() - held);
            EBB_OWED.merge(id, held, Float::sum);
            EBB_RATE.put(id, (float) a.power().num("rate", 1.0));
        });
    }

    private static void payEbb(ServerLevel level, ServerPlayer player) {
        UUID id = player.getUUID();
        Float owed = EBB_OWED.get(id);
        if (owed == null || owed <= 0.0F) return;
        if (!player.isAlive()) {
            EBB_OWED.remove(id);
            return;
        }
        float pay = Math.min(owed, Math.max(0.5F, EBB_RATE.getOrDefault(id, 1.0F)));
        PAYING.add(id);
        try {
            if (player.hurtServer(level, player.damageSources().magic(), pay)) owed -= pay;
        } finally {
            PAYING.remove(id);
        }
        if (owed <= 0.01F) EBB_OWED.remove(id);
        else EBB_OWED.put(id, owed);
    }

    /** Damage still owed by an ebb lens, for tests. */
    public static float ebbOwed(Player player) {
        return EBB_OWED.getOrDefault(player.getUUID(), 0.0F);
    }

    private static void onDamagePost(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || event.getSource().getDirectEntity() != player) return;
        first(active(player), "lifesteal").ifPresent(a -> {
            float heal = (float) (event.getHealthDamage() * a.power().num("fraction", 0.15) * Math.min(1.0, a.strength()));
            if (heal > 0.0F) player.heal(heal);
        });
    }

    private static void onPickupXp(PlayerXpEvent.PickupXp event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        first(active(player), "xp_bonus").ifPresent(a -> {
            int extra = (int) Math.round(event.getOrb().getValue() * a.power().num("bonus", 0.5) * a.strength());
            if (extra > 0) player.giveExperiencePoints(extra);
        });
    }

    private static void onExperienceDrop(LivingExperienceDropEvent event) {
        if (!(event.getAttackingPlayer() instanceof ServerPlayer player)) return;
        first(active(player), "kill_xp").ifPresent(a -> {
            double mult = 1.0 + a.power().num("bonus", 0.5) * a.strength();
            event.setDroppedExperience((int) Math.round(event.getDroppedExperience() * mult));
        });
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        ACTIVE.remove(id);
        EBB_RATE.remove(id);
        MODIFIERS.remove(id);
    }

    /** The powers a player has right now on the server, for tests and Jade. */
    public static List<Active> activePowers(Player player) {
        return active(player);
    }
}
