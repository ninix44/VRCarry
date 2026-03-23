package org.vmstudio.vrcarry.forge;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.vrcarry.core.client.ExampleAddonClient;
import org.vmstudio.vrcarry.core.client.VRCarryLogic;
import org.vmstudio.vrcarry.core.common.VRCarryBlockHandler;
import org.vmstudio.vrcarry.core.common.VRCarryNetworking;
import org.vmstudio.vrcarry.core.common.VisorExample;
import org.vmstudio.vrcarry.core.server.VRCarryAddonServer;

import java.util.function.Supplier;

@Mod(VisorExample.MOD_ID)
public class ExampleMod {

    private static final String PROTOCOL_VERSION = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        VRCarryNetworking.PICKUP_BLOCK_PACKET,
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    public ExampleMod() {
        CHANNEL.registerMessage(0, PickupBlockPacket.class, PickupBlockPacket::encode, PickupBlockPacket::decode, PickupBlockPacket::handle);
        CHANNEL.registerMessage(1, PlaceBlockPacket.class, PlaceBlockPacket::encode, PlaceBlockPacket::decode, PlaceBlockPacket::handle);

        MinecraftForge.EVENT_BUS.register(this);

        if (!ModLoader.get().isDedicatedServer()) {
            VRCarryLogic.bridge = new VRCarryLogic.NetworkBridge() {
                @Override
                public void sendPickupBlock(BlockPos pos) {
                    CHANNEL.sendToServer(new PickupBlockPacket(pos));
                }

                @Override
                public void sendPlaceBlock(BlockPos pos, Direction direction) {
                    CHANNEL.sendToServer(new PlaceBlockPacket(pos, direction));
                }
            };
        }

        if (ModLoader.get().isDedicatedServer()) {
            VisorAPI.registerAddon(
                    new VRCarryAddonServer()
            );
        } else {
            VisorAPI.registerAddon(
                    new ExampleAddonClient()
            );
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        for (var player : event.getServer().getPlayerList().getPlayers()) {
            VRCarryBlockHandler.onCarryTick(player);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().player != null) {
            VRCarryLogic.tick();
        }
    }

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof ServerPlayer player && !VRCarryBlockHandler.canInteractWhileCarrying(player)) {
            event.setNewSpeed(0.0F);
        }
    }

    @SubscribeEvent
    public void onBreakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && !VRCarryBlockHandler.canInteractWhileCarrying(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !VRCarryBlockHandler.canInteractWhileCarrying(player)) {
            event.setCanceled(true);
        }
    }

    public static class PickupBlockPacket {
        private final BlockPos pos;

        public PickupBlockPacket(BlockPos pos) {
            this.pos = pos;
        }

        public static void encode(PickupBlockPacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
        }

        public static PickupBlockPacket decode(FriendlyByteBuf buf) {
            return new PickupBlockPacket(buf.readBlockPos());
        }

        public static void handle(PickupBlockPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var player = ctx.get().getSender();
                if (player != null) {
                    VRCarryBlockHandler.tryPickupBlock(player, msg.pos);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class PlaceBlockPacket {
        private final BlockPos pos;
        private final Direction direction;

        public PlaceBlockPacket(BlockPos pos, Direction direction) {
            this.pos = pos;
            this.direction = direction;
        }

        public static void encode(PlaceBlockPacket msg, FriendlyByteBuf buf) {
            buf.writeBlockPos(msg.pos);
            buf.writeEnum(msg.direction);
        }

        public static PlaceBlockPacket decode(FriendlyByteBuf buf) {
            return new PlaceBlockPacket(buf.readBlockPos(), buf.readEnum(Direction.class));
        }

        public static void handle(PlaceBlockPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                var player = ctx.get().getSender();
                if (player != null) {
                    VRCarryBlockHandler.tryPlaceBlock(player, msg.pos, msg.direction);
                }
            });
            ctx.get().setPacketHandled(true);
        }
    }
}
