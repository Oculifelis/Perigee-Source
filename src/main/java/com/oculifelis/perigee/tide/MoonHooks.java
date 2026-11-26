package com.oculifelis.perigee.tide;

import com.fundamentum.moon.NightEvent;
import com.fundamentum.moon.NightEvents;
import com.oculifelis.perigee.Perigee;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Records other special nights in the {@link MoonLog} so basins can follow them: Fundamentum night events (such as
 * Prismatic Anomaly's Prismatic Rift) and Ars Moriendi's moons. Ars Moriendi is reached by name only, so Perigee
 * never needs it on the class path.
 */
public final class MoonHooks {
    private static final Logger LOG = LoggerFactory.getLogger(Perigee.MODID);

    private MoonHooks() {}

    public static void init() {
        NightEvents.addListener(new NightEvents.Listener() {
            @Override
            public void onStart(ServerLevel level, NightEvent event) {
                MoonLog.record(level, com.fundamentum.moon.Moons.tonight(level), event.id());
            }

            @Override
            public void whileUp(ServerLevel level, NightEvent event) {
                MoonLog.record(level, com.fundamentum.moon.Moons.tonight(level), event.id());
            }
        });
        if (ModList.get().isLoaded("arsmoriendi")) hookArsMoriendi();
    }

    private static void hookArsMoriendi() {
        try {
            Class<?> api = Class.forName("dev.arsmoriendi.api.LunarMoons");
            Class<?> listener = Class.forName("dev.arsmoriendi.api.LunarMoons$Listener");
            Object proxy = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (self, method, args) -> {
                if (method.getName().equals("onRise") && args != null && args.length == 2
                    && args[0] instanceof ServerLevel level && args[1] != null) {
                    Method id = args[1].getClass().getMethod("id");
                    Object value = id.invoke(args[1]);
                    if (value != null) MoonLog.record(level, com.fundamentum.moon.Moons.tonight(level), value.toString());
                }
                if (method.isDefault()) return null;
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(self);
                    case "equals" -> self == args[0];
                    case "toString" -> "PerigeeLunarHook";
                    default -> null;
                };
            });
            api.getMethod("addListener", listener).invoke(null, proxy);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOG.warn("Could not follow Ars Moriendi moons: {}", e.toString());
        }
    }
}
