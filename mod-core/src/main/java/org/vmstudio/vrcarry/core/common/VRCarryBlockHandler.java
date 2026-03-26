package org.vmstudio.vrcarry.core.common;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.vmstudio.vrcarry.core.common.VRCarryData.CarryType;

import java.util.UUID;

public class VRCarryBlockHandler {

    private static final double MAX_PICKUP_DISTANCE = 3.0D;

    public static boolean isSupportedCarryBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (state.isAir()) {
            return false;
        }

        if (state.getBlock() instanceof BedBlock) {
            return true;
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
        return tryPickupBlock(player, pos, null);
    }

    public static boolean tryPickupBlock(ServerPlayer player, BlockPos pos, @Nullable Direction pickupFace) {
        if (!canCarryGeneral(player, Vec3.atCenterOf(pos))) {
            return false;
        }

        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);

        if (state.getBlock() instanceof BedBlock) {
            return tryPickupBed(player, pos, state);
        }

        Direction restrictedPickupFace = getRestrictedPickupFace(state);
        if (restrictedPickupFace != null && pickupFace != restrictedPickupFace) {
            return false;
        }

        if (!isSupportedCarryBlock(level, pos)) {
            return false;
        }

        if (state.getDestroySpeed(level, pos) == -1.0F && !player.isCreative()) {
            return false;
        }

        VRCarryData carryData = VRCarryDataManager.getCarryData(player);
        carryData.setBlock(state, blockEntity, pickupFace);

        level.removeBlockEntity(pos);
        level.removeBlock(pos, false);

        VRCarryDataManager.setCarryData(player, carryData);
        level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 0.6F);
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    public static boolean tryPlaceBlock(ServerPlayer player, BlockPos pos, Direction facing) {
        VRCarryData carryData = VRCarryDataManager.getCarryData(player);
        if (!carryData.isCarrying(CarryType.BLOCK) && !carryData.isCarrying(CarryType.BED)) {
            return false;
        }

        if (player.tickCount == carryData.getTick()) {
            return false;
        }

        if (carryData.isCarrying(CarryType.BED)) {
            return tryPlaceBed(player, pos, facing, carryData);
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

        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 220, 0, false, false, true));

        if (carryData.isCarrying(CarryType.BED)) {
            tickBedOccupant(player, carryData);
        }
    }

    public static boolean canInteractWhileCarrying(ServerPlayer player) {
        return !VRCarryDataManager.getCarryData(player).isCarrying();
    }

    @Nullable
    public static Direction getRestrictedPickupFace(BlockState state) {
        Block block = state.getBlock();
        if (!(block instanceof ChestBlock)
            && !(block instanceof EnderChestBlock)
            && !(block instanceof AbstractFurnaceBlock)
            && !(block instanceof LecternBlock)) {
            return null;
        }

        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING);
        }

        for (Property<?> property : state.getProperties()) {
            if (property instanceof DirectionProperty directionProperty && "facing".equals(directionProperty.getName())) {
                Direction direction = state.getValue(directionProperty);
                if (direction.getAxis().isHorizontal()) {
                    return direction;
                }
            }
        }

        return null;
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

    private static boolean tryPickupBed(ServerPlayer player, BlockPos clickedPos, BlockState clickedState) {
        Level level = player.level();
        BlockPos footPos = getBedPartPos(clickedPos, clickedState, BedPart.FOOT);
        BlockPos headPos = getBedPartPos(clickedPos, clickedState, BedPart.HEAD);
        BlockState footState = level.getBlockState(footPos);
        BlockState headState = level.getBlockState(headPos);

        if (!(footState.getBlock() instanceof BedBlock) || !(headState.getBlock() instanceof BedBlock)) {
            return false;
        }

        if (footState.getValue(BedBlock.PART) != BedPart.FOOT || headState.getValue(BedBlock.PART) != BedPart.HEAD) {
            return false;
        }

        VRCarryData carryData = VRCarryDataManager.getCarryData(player);
        LivingEntity occupant = findSleepingOccupant(level, footPos, headPos);
        UUID occupantUuid = occupant != null ? occupant.getUUID() : null;

        carryData.setBed(footState, headState, occupantUuid);

        level.removeBlock(headPos, false);
        level.removeBlock(footPos, false);

        if (occupant != null) {
            occupant.stopRiding();
            occupant.ejectPassengers();
            occupant.startRiding(player, true);
            occupant.setPose(Pose.SLEEPING);
            occupant.setDeltaMovement(Vec3.ZERO);
            Vec3 offset = getBedPassengerOffset(player);
            occupant.setPos(player.getX() + offset.x, player.getY() + offset.y, player.getZ() + offset.z);
        }

        VRCarryDataManager.setCarryData(player, carryData);
        level.playSound(null, clickedPos, footState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.9F, 0.8F);
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    private static boolean tryPlaceBed(ServerPlayer player, BlockPos pos, Direction facing, VRCarryData carryData) {
        Level level = player.level();
        BlockState footState = carryData.getBedFootState();
        BlockState headState = carryData.getBedHeadState();
        Direction bedFacing = resolveBedFacing(footState, headState, player.getDirection());

        BlockPlaceContext context = createPlacementContext(player, pos, facing);
        if (!level.getBlockState(pos).canBeReplaced(context)) {
            pos = pos.relative(facing);
            context = createPlacementContext(player, pos, facing);
        }

        BlockPos footPos = pos;
        BlockPos headPos = footPos.relative(bedFacing);
        BlockPlaceContext headContext = createPlacementContext(player, headPos, facing);

        footState = footState.setValue(BedBlock.FACING, bedFacing).setValue(BedBlock.PART, BedPart.FOOT);
        headState = headState.setValue(BedBlock.FACING, bedFacing).setValue(BedBlock.PART, BedPart.HEAD);

        boolean canPlace = !level.isOutsideBuildHeight(footPos)
            && !level.isOutsideBuildHeight(headPos)
            && footState.canSurvive(level, footPos)
            && headState.canSurvive(level, headPos)
            && level.getBlockState(footPos).canBeReplaced(context)
            && level.getBlockState(headPos).canBeReplaced(headContext)
            && level.isUnobstructed(footState, footPos, CollisionContext.empty())
            && level.isUnobstructed(headState, headPos, CollisionContext.empty());

        if (!canPlace) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.LAVA_POP, SoundSource.PLAYERS, 0.5F, 0.5F);
            return false;
        }

        Entity occupant = getBedOccupantEntity(player.level(), carryData);

        level.setBlockAndUpdate(footPos, footState);
        level.setBlockAndUpdate(headPos, headState);

        level.updateNeighborsAt(footPos.below(), level.getBlockState(footPos.below()).getBlock());
        level.updateNeighborsAt(headPos.below(), level.getBlockState(headPos.below()).getBlock());
        carryData.clear();
        VRCarryDataManager.setCarryData(player, carryData);
        releaseBedOccupant(occupant, headPos);
        level.playSound(null, footPos, footState.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.6F);
        player.swing(InteractionHand.MAIN_HAND, true);
        return true;
    }

    private static void tickBedOccupant(ServerPlayer carrier, VRCarryData carryData) {
        Entity occupant = getBedOccupantEntity(carrier.level(), carryData);
        if (!(occupant instanceof LivingEntity livingOccupant) || !occupant.isAlive()) {
            return;
        }

        if (occupant.getVehicle() != carrier) {
            occupant.startRiding(carrier, true);
        }

        Vec3 offset = getBedPassengerOffset(carrier);
        occupant.setPos(carrier.getX() + offset.x, carrier.getY() + offset.y, carrier.getZ() + offset.z);
        float bedYaw = getBedYaw(carryData.getBedFootState().getValue(BedBlock.FACING));
        occupant.setYRot(bedYaw);
        occupant.setYHeadRot(bedYaw);
        occupant.setYBodyRot(bedYaw);
        livingOccupant.setPose(Pose.SLEEPING);
        occupant.setDeltaMovement(Vec3.ZERO);
        occupant.fallDistance = 0.0F;
    }

    private static void releaseBedOccupant(Entity occupant, BlockPos headPos) {
        if (occupant == null) {
            return;
        }

        occupant.stopRiding();
        occupant.teleportTo(headPos.getX() + 0.5D, headPos.getY() + 0.5625D, headPos.getZ() + 0.5D);

        if (occupant instanceof LivingEntity livingOccupant) {
            livingOccupant.setPose(Pose.STANDING);
        }

        if (occupant instanceof ServerPlayer sleepingPlayer) {
            sleepingPlayer.startSleepInBed(headPos);
        }
    }

    private static Entity getBedOccupantEntity(Level level, VRCarryData carryData) {
        UUID occupantUuid = carryData.getBedOccupantUuid();
        if (occupantUuid == null || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return serverLevel.getEntity(occupantUuid);
    }

    private static LivingEntity findSleepingOccupant(Level level, BlockPos footPos, BlockPos headPos) {
        AABB searchBox = new AABB(footPos).minmax(new AABB(headPos)).inflate(2.0D);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, searchBox)) {
            if (!entity.isSleeping()) {
                continue;
            }

            var sleepingPos = entity.getSleepingPos();
            if (sleepingPos.isPresent() && (sleepingPos.get().equals(footPos) || sleepingPos.get().equals(headPos))) {
                return entity;
            }
        }

        return null;
    }

    private static BlockPos getBedPartPos(BlockPos clickedPos, BlockState clickedState, BedPart targetPart) {
        if (clickedState.getValue(BedBlock.PART) == targetPart) {
            return clickedPos;
        }

        Direction direction = clickedState.getValue(BedBlock.FACING);
        return targetPart == BedPart.HEAD ? clickedPos.relative(direction) : clickedPos.relative(direction.getOpposite());
    }

    private static Direction resolveBedFacing(BlockState footState, BlockState headState, Direction fallback) {
        if (footState.hasProperty(BedBlock.FACING)) {
            return footState.getValue(BedBlock.FACING);
        }

        if (headState.hasProperty(BedBlock.FACING)) {
            return headState.getValue(BedBlock.FACING);
        }

        return fallback.getAxis().isHorizontal() ? fallback : Direction.NORTH;
    }

    public static boolean isLockedBedPassenger(Entity entity) {
        if (entity == null || entity.level().getServer() == null) {
            return false;
        }

        for (ServerPlayer carrier : entity.level().getServer().getPlayerList().getPlayers()) {
            VRCarryData carryData = VRCarryDataManager.getCarryData(carrier);
            if (!carryData.isCarrying(CarryType.BED)) {
                continue;
            }

            UUID occupantUuid = carryData.getBedOccupantUuid();
            if (occupantUuid != null && occupantUuid.equals(entity.getUUID())) {
                return true;
            }
        }

        return false;
    }

    public static boolean shouldOverrideBedPassengerPosition(Entity carrier, Entity passenger) {
        if (!(carrier instanceof Player player)) {
            return false;
        }

        VRCarryData carryData = VRCarryDataManager.getCarryData(player);
        return carryData.isCarrying(CarryType.BED)
            && carryData.getBedOccupantUuid() != null
            && carryData.getBedOccupantUuid().equals(passenger.getUUID());
    }

    public static Vec3 getBedPassengerOffset(Entity carrier) {
        Direction facing = null;
        if (carrier instanceof Player player) {
            VRCarryData carryData = VRCarryDataManager.getCarryData(player);
            if (carryData.isCarrying(CarryType.BED)) {
                facing = carryData.getBedFootState().getValue(BedBlock.FACING);
            }
        }

        if (facing == null) {
            facing = Direction.SOUTH;
        }

        Vec3 forward = new Vec3(facing.getStepX(), 0.0D, facing.getStepZ());
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);
        return forward.scale(0.45D).add(right.scale(0.02D)).add(0.0D, 0.62D, 0.0D);
    }

    public static float getBedYaw(Direction facing) {
        return switch (facing) {
            case NORTH -> 180.0F;
            case SOUTH -> 0.0F;
            case WEST -> 270.0F;
            case EAST -> 90.0F;
            default -> 180.0F;
        };
    }

    public static float getLockedBedPassengerScale(Entity entity) {
        return isLockedBedPassenger(entity) ? 0.92F : 1.0F;
    }

    public static Direction getLockedBedFacing(Entity entity) {
        if (entity == null || entity.level().getServer() == null) {
            return null;
        }

        for (ServerPlayer carrier : entity.level().getServer().getPlayerList().getPlayers()) {
            VRCarryData carryData = VRCarryDataManager.getCarryData(carrier);
            if (!carryData.isCarrying(CarryType.BED)) {
                continue;
            }

            UUID occupantUuid = carryData.getBedOccupantUuid();
            if (occupantUuid != null && occupantUuid.equals(entity.getUUID())) {
                return carryData.getBedFootState().getValue(BedBlock.FACING);
            }
        }

        return null;
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
