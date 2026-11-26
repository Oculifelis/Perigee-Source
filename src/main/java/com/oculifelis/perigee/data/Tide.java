package com.oculifelis.perigee.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/** One tide from {@code data/<ns>/perigee/tides/*.json}: the moon phases it covers and what its basins yield. */
public record Tide(String name, List<Integer> phases, Identifier brine, Identifier salt, Identifier lens, int colour) {
    public static Tide fromJson(JsonObject obj) {
        String name = obj.has("name") ? obj.get("name").getAsString() : "full";
        List<Integer> phases = new ArrayList<>();
        if (obj.has("phases")) {
            for (JsonElement e : obj.getAsJsonArray("phases")) phases.add(e.getAsInt());
        }
        Identifier brine = Identifier.parse(obj.get("brine").getAsString());
        Identifier salt = Identifier.parse(obj.get("salt").getAsString());
        Identifier lens = obj.has("lens") ? Identifier.parse(obj.get("lens").getAsString()) : null;
        int colour = obj.has("colour") ? Integer.parseInt(obj.get("colour").getAsString().replace("#", ""), 16) : 0xFFFFFF;
        return new Tide(name, List.copyOf(phases), brine, salt, lens, colour);
    }

    public boolean covers(int phase) {
        return phases.contains(phase);
    }
}
