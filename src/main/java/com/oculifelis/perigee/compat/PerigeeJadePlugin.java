package com.oculifelis.perigee.compat;

import com.oculifelis.perigee.Perigee;
import com.oculifelis.perigee.world.TidalStandBlock;
import com.oculifelis.perigee.world.TidalStandBlockEntity;
import com.oculifelis.perigee.world.TideBasinBlock;
import com.oculifelis.perigee.world.TideBasinBlockEntity;
import com.oculifelis.perigee.world.TideDialBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

@WailaPlugin
public class PerigeeJadePlugin implements IWailaPlugin {
    public static final Identifier BASIN_UID = Perigee.id("tide_basin");
    public static final Identifier STAND_UID = Perigee.id("tidal_stand");
    public static final Identifier DIAL_UID = Perigee.id("tide_dial");

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(BasinData.INSTANCE, TideBasinBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(BasinClient.INSTANCE, TideBasinBlock.class);
        registration.registerBlockComponent(StandClient.INSTANCE, TidalStandBlock.class);
        registration.registerBlockComponent(DialClient.INSTANCE, TideDialBlock.class);
    }

    /** The basin state needs the server moon log, so the server sends the finished line. */
    private enum BasinData implements StreamServerDataProvider<BlockAccessor, Component> {
        INSTANCE;

        @Override
        public Component streamData(BlockAccessor accessor) {
            if (accessor.getBlockEntity() instanceof TideBasinBlockEntity basin && accessor.getLevel() instanceof ServerLevel level) {
                return TideBasinBlock.describe(basin.state(level));
            }
            return Component.empty();
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Component> streamCodec() {
            return ComponentSerialization.STREAM_CODEC;
        }

        @Override
        public Identifier getUid() {
            return BASIN_UID;
        }
    }

    private enum BasinClient implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BasinData.INSTANCE.decodeFromData(accessor).ifPresent(line -> {
                if (!line.getString().isEmpty()) tooltip.add(line.copy().withStyle(ChatFormatting.AQUA));
            });
        }

        @Override
        public Identifier getUid() {
            return BASIN_UID;
        }
    }

    /** The stand item and pour count are synced already. */
    private enum StandClient implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlockEntity() instanceof TidalStandBlockEntity stand && !stand.item().isEmpty()) {
                tooltip.add(Component.translatable("jade.perigee.stand_item", stand.item().getHoverName()).withStyle(ChatFormatting.GRAY));
                if (stand.poured() > 0) {
                    tooltip.add(Component.translatable("jade.perigee.stand_poured", stand.poured()).withStyle(ChatFormatting.GRAY));
                }
            }
        }

        @Override
        public Identifier getUid() {
            return STAND_UID;
        }
    }

    private enum DialClient implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            int power = accessor.getBlockState().getValue(TideDialBlock.POWER);
            tooltip.add(Component.translatable("jade.perigee.dial_power", power).withStyle(ChatFormatting.RED));
        }

        @Override
        public Identifier getUid() {
            return DIAL_UID;
        }
    }
}
