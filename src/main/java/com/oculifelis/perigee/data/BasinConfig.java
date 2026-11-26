package com.oculifelis.perigee.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/** Basin timings and strengths from {@code data/<ns>/perigee/basin/*.json}. Missing fields keep the defaults. */
public record BasinConfig(int condenseTicks, int bottles, int dryTicks, int salts, List<Double> falloff,
                          List<Integer> phasePull, List<String> dimensions) {
    public static final BasinConfig DEFAULT = new BasinConfig(5000, 3, 6000, 2,
        List.of(1.0, 0.66, 0.33, 0.0, 0.0),
        List.of(15, 11, 7, 11, 15, 11, 7, 11),
        List.of("minecraft:overworld"));

    public static BasinConfig fromJson(JsonObject obj) {
        int condense = obj.has("condense_ticks") ? obj.get("condense_ticks").getAsInt() : DEFAULT.condenseTicks;
        int bottles = obj.has("bottles") ? obj.get("bottles").getAsInt() : DEFAULT.bottles;
        int dry = obj.has("dry_ticks") ? obj.get("dry_ticks").getAsInt() : DEFAULT.dryTicks;
        int salts = obj.has("salts") ? obj.get("salts").getAsInt() : DEFAULT.salts;
        List<Double> falloff = new ArrayList<>();
        if (obj.has("falloff")) for (JsonElement e : obj.getAsJsonArray("falloff")) falloff.add(e.getAsDouble());
        List<Integer> pull = new ArrayList<>();
        if (obj.has("phase_pull")) for (JsonElement e : obj.getAsJsonArray("phase_pull")) pull.add(e.getAsInt());
        List<String> dims = new ArrayList<>();
        if (obj.has("dimensions")) for (JsonElement e : obj.getAsJsonArray("dimensions")) dims.add(e.getAsString());
        return new BasinConfig(Math.max(1, condense), Math.max(1, bottles), Math.max(0, dry), Math.max(0, salts),
            falloff.isEmpty() ? DEFAULT.falloff : List.copyOf(falloff),
            pull.size() == 8 ? List.copyOf(pull) : DEFAULT.phasePull,
            dims.isEmpty() ? DEFAULT.dimensions : List.copyOf(dims));
    }

    /** Strength of a lens worn {@code distance} phases away from its own tide. */
    public double falloffAt(int distance) {
        if (distance < 0) return 0.0;
        return distance < falloff.size() ? falloff.get(distance) : 0.0;
    }

    public int pull(int phase) {
        return phasePull.get(Math.floorMod(phase, 8));
    }
}
