package org.vmstudio.vrcarry.core.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.vmstudio.vrcarry.core.common.VRCarryData.CarryType;

public class VRCarryBlockHandler {

    private static final double MAX_PICKUP_DISTANCE = 3.0D;

    public static boolean isSupportedCarryBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (state.isAir()) {
            return false;
        }

        if (blockEntity == null && !state.hasBlockEntity()) {
            return false;
        }

        if (blockEntity != null) {
            var nbt = blockEntity.saveWithId();
            if (nbt.contains("Lock") && !nbt.getString("Lock").isEmpty()) {
                return false;
            }
        }

        return true;
    }

    public static boolean tryPickupBlock(ServerPlayer player, BlockPos pos) {
        if (!canCarryGeneral(player, Vec3.atCenterOf(pos))) {
            return false;
        }

        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (!isSupportedCarryBlock(level, pos)) {
            return false;
        }

        if (state.getDestroySpeed(level, pos) == -1.0F && !player.isCreative()) {
            return false;
        }

        VRCarryData carryData = VRCarryDataManager.getCarryData(player);
        carryData.setBlock(state, blockEntity);

        level.removeBlockEntity(pos);
        level.removeBlock(pos, false);

        VRCarryDataManager.setCarryData(player, carryData);
        level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 0.6F);
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    public static boolean tryPlaceBlock(ServerPlayer player, BlockPos pos, Direction facing) {
        VRCarryData carryData = VRCarryDataManager.getCarryData(player);
        if (!carryData.isCarrying(CarryType.BLOCK)) {
            return false;
        }

        if (player.tickCount == carryData.getTick()) {
            return false;
        }

        Level level = player.level();
        BlockState carriedState = carryData.getBlockState();

        BlockPlaceContext context = createPlacementContext(player, pos, facing);
        if (!level.getBlockState(pos).canBeReplaced(context)) {
            pos = pos.relative(facing);
            context = createPlacementContext(player, pos, facing);
        }

        BlockState placementState = getPlacementState(carriedState, player, context, pos);
        boolean canPlace = placementState.canSurvive(level, pos)
            && level.mayInteract(player, pos)
            && level.getBlockState(pos).canBeReplaced(context)
            && level.isUnobstructed(placementState, pos, CollisionContext.of(player));

        if (!canPlace) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.5F, 0.5F);
            return false;
        }

        BlockEntity blockEntity = carryData.getBlockEntity(pos);
        level.setBlockAndUpdate(pos, placementState);
        if (blockEntity != null) {
            blockEntity.setBlockState(placementState);
            level.setBlockEntity(blockEntity);
        }

        level.updateNeighborsAt(pos.below(), level.getBlockState(pos.below()).getBlock());
        carryData.clear();
        VRCarryDataManager.setCarryData(player, carryData);
        level.playSound(null, pos, placementState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.6F);
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    public static void onCarryTick(ServerPlayer player) {
        VRCarryData carryData = VRCarryDataManager.getCarryData(player);
        if (!carryData.isCarrying()) {
            return;
        }

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, 1, false, false, false));
    }

    public static boolean canInteractWhileCarrying(ServerPlayer player) {
        return !VRCarryDataManager.getCarryData(player).isCarrying();
    }

    private static boolean canCarryGeneral(ServerPlayer player, Vec3 pos) {
        if (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty()) {
            return false;
        }

        if (player.position().distanceTo(pos) > MAX_PICKUP_DISTANCE) {
            return false;
        }

        VRCarryData carryData = VRCarryDataManager.getCarryData(player);
        if (carryData.isCarrying()) {
            return false;
        }

        if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR || player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
            return false;
        }

        return true;
    }

    private static BlockPlaceContext createPlacementContext(ServerPlayer player, BlockPos pos, Direction facing) {
        return new BlockPlaceContext(player, InteractionHand.MAIN_HAND, ItemStack.EMPTY, BlockHitResult.miss(player.position(), facing, pos));
    }

    private static BlockState getPlacementState(BlockState state, ServerPlayer player, BlockPlaceContext context, BlockPos pos) {
        BlockState placementState = state.getBlock().getStateForPlacement(context);
        if (placementState == null || placementState.getBlock() != state.getBlock()) {
            placementState = state;
        }

        for (Property<?> property : placementState.getProperties()) {
            if (property instanceof DirectionProperty || property.getValueClass() == Direction.Axis.class || "type".equals(property.getName())) {
                state = updateProperty(state, placementState, property);
            }
        }

        BlockState updatedState = Block.updateFromNeighbourShapes(state, player.level(), pos);
        if (updatedState.getBlock() == state.getBlock()) {
            state = updatedState;
        }

        if (placementState.hasProperty(BlockStateProperties.WATERLOGGED) && state.hasProperty(BlockStateProperties.WATERLOGGED)) {
            state = state.setValue(BlockStateProperties.WATERLOGGED, placementState.getValue(BlockStateProperties.WATERLOGGED));
        }

        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState updateProperty(BlockState state, BlockState otherState, Property property) {
        if (state.hasProperty(property) && otherState.hasProperty(property)) {
            return state.setValue(property, otherState.getValue(property));
        }
        return state;
    }
}
