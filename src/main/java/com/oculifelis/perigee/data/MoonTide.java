package com.oculifelis.perigee.data;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

/** How a special moon changes basins, from {@code data/<ns>/perigee/moons/*.json}. */
public record MoonTide(String moon, double speed, Identifier brine) {
    public static MoonTide fromJson(JsonObject obj) {
        String moon = obj.get("moon").getAsString();
        double speed = obj.has("speed") ? obj.get("speed").getAsDouble() : 1.0;
        Identifier brine = obj.has("brine") ? Identifier.parse(obj.get("brine").getAsString()) : null;
        return new MoonTide(moon, Math.max(0.0, speed), brine);
    }
}
