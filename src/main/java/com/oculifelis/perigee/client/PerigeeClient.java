package com.oculifelis.perigee.client;

import com.fundamentum.moon.Moons;
import com.oculifelis.perigee.data.PerigeeTables;
import com.oculifelis.perigee.data.Tide;
import com.oculifelis.perigee.registry.PerigeeBlocks;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Client only: connected moon glass, tinted by tonight's moon phase in the tide's own colour. */
public final class PerigeeClient {
    /** Fallback colours by phase when the tide table has not reached the client. */
    private static final int[] PHASE_COLOURS = {0xE8F0FF, 0x8FB4E8, 0x8FB4E8, 0x8FB4E8, 0x3A3F6B, 0xB8D8C8, 0xB8D8C8, 0xB8D8C8};
    private static int shownPhase = -1;

    private PerigeeClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(ConnectedGlassModel::register);
        modBus.addListener(PerigeeClient::tints);
        NeoForge.EVENT_BUS.addListener(PerigeeClient::tick);
    }

    private static void tints(RegisterColorHandlersEvent.BlockTintSources event) {
        BlockTintSource moon = new BlockTintSource() {
            @Override
            public int color(BlockState state) {
                return glassColour(0);
            }

            @Override
            public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
                return glassColour(phase());
            }
        };
        event.register(List.of(moon), PerigeeBlocks.MOON_GLASS.get(), PerigeeBlocks.MOON_GLASS_PANE.get());
    }

    /** The phase's tide colour, lifted toward white so the new moon still reads as glass. */
    public static int glassColour(int phase) {
        int c = PerigeeTables.tideForPhase(phase).map(Tide::colour).orElse(PHASE_COLOURS[Math.floorMod(phase, 8)]);
        int r = (c >> 16 & 255) * 3 / 5 + 102, g = (c >> 8 & 255) * 3 / 5 + 102, b = (c & 255) * 3 / 5 + 102;
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int phase() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? 0 : Moons.moonPhase(mc.level.getDefaultClockTime());
    }

    /** Tints are baked into chunk meshes, so a new phase redraws the world once (once per in-game day). */
    private static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            shownPhase = -1;
            return;
        }
        int now = phase();
        if (shownPhase >= 0 && now != shownPhase) mc.levelRenderer.allChanged();
        shownPhase = now;
    }
}
