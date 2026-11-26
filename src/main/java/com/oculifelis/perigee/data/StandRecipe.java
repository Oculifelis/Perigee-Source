package com.oculifelis.perigee.data;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

/**
 * One tidal stand recipe from {@code data/<ns>/perigee/stand_recipes/*.json}: pour {@code count} of {@code liquid}
 * over {@code input} to get {@code output}. With {@code fixed} the held item is kept and marked fixed instead.
 */
public record StandRecipe(Identifier input, Identifier liquid, int count, Identifier output, boolean fixed, Identifier returns) {
    public static StandRecipe fromJson(JsonObject obj) {
        Identifier input = Identifier.parse(obj.get("input").getAsString());
        Identifier liquid = Identifier.parse(obj.get("liquid").getAsString());
        int count = obj.has("count") ? obj.get("count").getAsInt() : 1;
        Identifier output = obj.has("output") ? Identifier.parse(obj.get("output").getAsString()) : input;
        boolean fixed = obj.has("fixed") && obj.get("fixed").getAsBoolean();
        Identifier returns = obj.has("returns") ? Identifier.parse(obj.get("returns").getAsString()) : null;
        return new StandRecipe(input, liquid, Math.max(1, count), output, fixed, returns);
    }
}
