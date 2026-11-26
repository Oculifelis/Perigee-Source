package com.oculifelis.perigee.tide;

import com.fundamentum.moon.Moon;
import com.fundamentum.moon.Moons;
import com.oculifelis.perigee.Perigee;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Map;
import java.util.TreeMap;

/**
 * Remembers which special moon was up on each night, so a basin that sat unloaded through a night still counts that
 * night's moon. Kept in the overworld's saved data; only the last {@link #KEEP} nights are held.
 */
public final class MoonLog extends SavedData {
    public static final int KEEP = 64;

    public static final SavedDataType<MoonLog> TYPE = new SavedDataType<>(
        Perigee.id("moon_log"), MoonLog::new,
        CompoundTag.CODEC.xmap(MoonLog::load, log -> log.save(new CompoundTag())));

    private final TreeMap<Long, String> nights = new TreeMap<>();

    public MoonLog() {}

    public static void init() {
        Moons.addListener(new Moons.Listener() {
            @Override
            public void onRise(ServerLevel level, Moon moon) {
                record(level, Moons.tonight(level), moon.id());
            }

            @Override
            public void whileUp(ServerLevel level, Moon moon) {
                record(level, Moons.tonight(level), moon.id());
            }
        });
    }

    public static MoonLog get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    public static void record(ServerLevel level, long night, String moonId) {
        MoonLog log = get(level);
        if (moonId == null || moonId.isEmpty()) return;
        if (moonId.equals(log.nights.get(night))) return;
        log.nights.put(night, moonId);
        while (log.nights.size() > KEEP) log.nights.pollFirstEntry();
        log.setDirty();
    }

    /** The special moon recorded for that night, or "" for a plain night. */
    public String moonOn(long night) {
        return nights.getOrDefault(night, "");
    }

    public static String moonOn(ServerLevel level, long night) {
        return get(level).moonOn(night);
    }

    private static MoonLog load(CompoundTag tag) {
        MoonLog log = new MoonLog();
        CompoundTag n = tag.getCompoundOrEmpty("nights");
        for (String key : n.keySet()) {
            try {
                log.nights.put(Long.parseLong(key), n.getStringOr(key, ""));
            } catch (NumberFormatException ignored) {
                // skip a damaged entry
            }
        }
        return log;
    }

    private CompoundTag save(CompoundTag tag) {
        CompoundTag n = new CompoundTag();
        for (Map.Entry<Long, String> e : nights.entrySet()) n.putString(Long.toString(e.getKey()), e.getValue());
        tag.put("nights", n);
        return tag;
    }
}
