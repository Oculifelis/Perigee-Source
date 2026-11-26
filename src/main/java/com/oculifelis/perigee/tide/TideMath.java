package com.oculifelis.perigee.tide;

import com.fundamentum.moon.Moons;
import com.oculifelis.perigee.data.BasinConfig;

import java.util.function.LongFunction;

/**
 * Pure timing math for tide basins, so it can be tested without a world. A basin condenses only while the moon is up
 * (each night window), and dries only while the sun is up (each day window). Nothing ticks: the state is worked out
 * from the fill time whenever it is looked at.
 */
public final class TideMath {
    public static final long DAY = 24000L;
    public static final long NIGHT_START = 13000L;
    public static final long NIGHT_END = 23000L;
    public static final long DAY_START = 1000L;
    public static final long DAY_END = 12000L;

    /** How many nights past the fill a basin is still followed; older fills just stay as they are. */
    public static final int MAX_NIGHTS = 64;

    private TideMath() {}

    /**
     * The exact game time a basin filled at {@code fill} finishes condensing, or -1 if it has not yet by {@code now}.
     * {@code speed} gives the condense rate for the night with that index (1.0 plain, a special moon may raise it).
     */
    public static long finishTime(long fill, long now, int condenseTicks, LongFunction<Double> speed) {
        if (fill < 0 || now < fill) return -1;
        double need = condenseTicks;
        long first = Math.floorDiv(fill, DAY);
        long last = Math.min(Math.floorDiv(now, DAY), first + MAX_NIGHTS);
        for (long d = first; d <= last; d++) {
            long start = Math.max(d * DAY + NIGHT_START, fill);
            long end = Math.min(d * DAY + NIGHT_END, now);
            if (end <= start) continue;
            double rate = Math.max(0.0, speed.apply(d));
            if (rate <= 0.0) continue;
            double gain = (end - start) * rate;
            if (gain >= need) return start + (long) Math.ceil(need / rate);
            need -= gain;
        }
        return -1;
    }

    /** Fraction 0..1 of the condensing done by {@code now}. */
    public static double progress(long fill, long now, int condenseTicks, LongFunction<Double> speed) {
        if (fill < 0 || now <= fill) return 0.0;
        double done = 0;
        long first = Math.floorDiv(fill, DAY);
        long last = Math.min(Math.floorDiv(now, DAY), first + MAX_NIGHTS);
        for (long d = first; d <= last && done < condenseTicks; d++) {
            long start = Math.max(d * DAY + NIGHT_START, fill);
            long end = Math.min(d * DAY + NIGHT_END, now);
            if (end > start) done += (end - start) * Math.max(0.0, speed.apply(d));
        }
        return Math.min(1.0, done / condenseTicks);
    }

    /** The night index a finish time belongs to. */
    public static long nightOf(long time) {
        return Math.floorDiv(time, DAY);
    }

    /** Moon phase of the night with that index (0 full, 4 new), matching Fundamentum's moon clock. */
    public static int phaseOfNight(long night) {
        return Moons.moonPhase(night * DAY);
    }

    /** Daylight ticks between {@code from} and {@code now}. */
    public static long sunTicks(long from, long now) {
        if (from < 0 || now <= from) return 0;
        long total = 0;
        long first = Math.floorDiv(from, DAY);
        long last = Math.min(Math.floorDiv(now, DAY), first + MAX_NIGHTS);
        for (long d = first; d <= last; d++) {
            long start = Math.max(d * DAY + DAY_START, from);
            long end = Math.min(d * DAY + DAY_END, now);
            if (end > start) total += end - start;
        }
        return total;
    }

    /** Salts left once a basin with {@code left} of {@code bottles} bottles still in it has dried. */
    public static int saltsFor(BasinConfig cfg, int left) {
        if (left <= 0) return 0;
        return (int) Math.ceil(cfg.salts() * (double) left / cfg.bottles());
    }

    /** Shortest way round the phase circle between two phases (0..4). */
    public static int phaseDistance(int a, int b) {
        int d = Math.floorMod(a - b, 8);
        return Math.min(d, 8 - d);
    }

    /** True when the time of day sits inside the night window. */
    public static boolean nightTime(long time) {
        long t = Math.floorMod(time, DAY);
        return t >= NIGHT_START && t < NIGHT_END;
    }

    /** Ticks until the next night start or end boundary. */
    public static long ticksToBoundary(long time) {
        long t = Math.floorMod(time, DAY);
        if (t < NIGHT_START) return NIGHT_START - t;
        if (t < NIGHT_END) return NIGHT_END - t;
        return DAY - t + NIGHT_START;
    }
}
