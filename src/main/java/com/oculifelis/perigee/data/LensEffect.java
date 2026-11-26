package com.oculifelis.perigee.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * What a lens does in a Phase Medallion, from {@code data/<ns>/perigee/lens_effects/*.json}. Its strength follows how
 * close tonight's phase is to the lens's tide; {@code always} lenses work at full strength every phase.
 *
 * <pre>{"lens": "perigee:full_lens", "tide": "full", "powers": [{"type": "water_walk"}]}</pre>
 */
public record LensEffect(Identifier lens, String tide, boolean always, List<Power> powers) {
    /** One power: its type, a strength scale, and the type's own numbers. */
    public record Power(String type, double scale, JsonObject params) {
        public double num(String key, double fallback) {
            return params.has(key) ? params.get(key).getAsDouble() : fallback;
        }

        public String str(String key, String fallback) {
            return params.has(key) ? params.get(key).getAsString() : fallback;
        }
    }

    public static LensEffect fromJson(JsonObject obj) {
        Identifier lens = Identifier.parse(obj.get("lens").getAsString());
        String tide = obj.has("tide") ? obj.get("tide").getAsString() : "";
        boolean always = obj.has("always") && obj.get("always").getAsBoolean();
        List<Power> powers = new ArrayList<>();
        if (obj.has("powers")) {
            for (JsonElement e : obj.getAsJsonArray("powers")) {
                JsonObject p = e.getAsJsonObject();
                powers.add(new Power(p.get("type").getAsString(), p.has("scale") ? p.get("scale").getAsDouble() : 1.0, p));
            }
        }
        return new LensEffect(lens, tide, always, List.copyOf(powers));
    }
}
