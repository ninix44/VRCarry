package org.vmstudio.vrcarry.fabric;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.vrcarry.core.client.ExampleAddonClient;
import org.vmstudio.vrcarry.core.client.VRCarryLogic;
import org.vmstudio.vrcarry.core.common.VRCarryBlockHandler;
import org.vmstudio.vrcarry.core.common.VRCarryNetworking;
import org.vmstudio.vrcarry.core.server.VRCarryAddonServer;

public class ExampleMod implements ModInitializer {
    @Override
    public void onInitialize() {
        ServerPlayNetworking.registerGlobalReceiver(VRCarryNetworking.PICKUP_BLOCK_PACKET, (server, player, handler, buf, responseSender) -> {
            var pos = buf.readBlockPos();
            var pickupFace = buf.readEnum(Direction.class);
            server.execute(() -> VRCarryBlockHandler.tryPickupBlock(player, pos, pickupFace));
        });

        ServerPlayNetworking.registerGlobalReceiver(VRCarryNetworking.PLACE_BLOCK_PACKET, (server, player, handler, buf, responseSender) -> {
            var pos = buf.readBlockPos();
            var direction = buf.readEnum(Direction.class);
            server.execute(() -> VRCarryBlockHandler.tryPlaceBlock(player, pos, direction));
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                VRCarryBlockHandler.onCarryTick(player);
            }
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
            !(player instanceof ServerPlayer serverPlayer) || VRCarryBlockHandler.canInteractWhileCarrying(serverPlayer)
        );

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (player instanceof ServerPlayer serverPlayer && !VRCarryBlockHandler.canInteractWhileCarrying(serverPlayer)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer && !VRCarryBlockHandler.canInteractWhileCarrying(serverPlayer)) {
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        if (ModLoader.get().isDedicatedServer()) {
            VisorAPI.registerAddon(
                    new VRCarryAddonServer()
            );
        } else {
            VisorAPI.registerAddon(
                    new ExampleAddonClient()
            );

            VRCarryLogic.bridge = new VRCarryLogic.NetworkBridge() {
                @Override
                public void sendPickupBlock(BlockPos pos, Direction pickupFace) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeBlockPos(pos);
                    buf.writeEnum(pickupFace);
                    ClientPlayNetworking.send(VRCarryNetworking.PICKUP_BLOCK_PACKET, buf);
                }

                @Override
                public void sendPlaceBlock(BlockPos pos, Direction direction) {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    buf.writeBlockPos(pos);
                    buf.writeEnum(direction);
                    ClientPlayNetworking.send(VRCarryNetworking.PLACE_BLOCK_PACKET, buf);
                }
            };

            ClientTickEvents.END_CLIENT_TICK.register(client -> VRCarryLogic.tick());
        }
    }
}
