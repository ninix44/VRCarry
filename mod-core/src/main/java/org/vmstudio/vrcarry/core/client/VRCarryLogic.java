package org.vmstudio.vrcarry.core.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.player.VRLocalPlayer;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseClient;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.api.common.player.VRPose;
import org.vmstudio.vrcarry.core.common.VRCarryBlockHandler;
import org.vmstudio.vrcarry.core.common.VRCarryData;
import org.vmstudio.vrcarry.core.common.VRCarryDataManager;

public class VRCarryLogic {

    public interface NetworkBridge {
        void sendPickupBlock(BlockPos pos);

        void sendPlaceBlock(BlockPos pos, Direction direction);
    }

    public static NetworkBridge bridge;

    private static final int PICKUP_HOLD_TICKS = 14;
    private static final int PLACE_HOLD_TICKS = 10;
    private static final double PICKUP_REACH = 0.02D;
    private static final double MAX_HAND_SPEED = 0.14D;
    private static final double MAX_PLACE_HEIGHT = 0.55D;

    private static int pickupTicks = 0;
    private static int placeTicks = 0;
    private static int actionCooldown = 0;

    private static Vec3 prevMainPos;
    private static Vec3 prevOffPos;
    private static Display.ItemDisplay carriedDisplay;
    private static BlockState renderedState;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused() || mc.screen != null) {
            return;
        }

        VRLocalPlayer vrPlayer = VisorAPI.client().getVRLocalPlayer();
        if (vrPlayer == null || !VisorAPI.clientState().playMode().canPlayVR()) {
            removeDisplay(mc);
            return;
        }

        if (actionCooldown > 0) {
            actionCooldown--;
        }

        PlayerPoseClient pose = vrPlayer.getPoseData(PlayerPoseType.TICK);
        updateCarriedDisplay(mc, pose);
        processHands(mc, pose);
    }

    private static void processHands(Minecraft mc, PlayerPoseClient pose) {
        Vec3 mainHandPos = getGrabPoint(pose.getMainHand());
        Vec3 offHandPos = getGrabPoint(pose.getOffhand());
        double mainSpeed = prevMainPos == null ? 0.0D : mainHandPos.distanceTo(prevMainPos);
        double offSpeed = prevOffPos == null ? 0.0D : offHandPos.distanceTo(prevOffPos);
        prevMainPos = mainHandPos;
        prevOffPos = offHandPos;

        VRCarryData carryData = VRCarryDataManager.getCarryData(mc.player);
        if (!carryData.isCarrying()) {
            handlePickup(mc, pose, mainHandPos, offHandPos, mainSpeed, offSpeed);
        } else {
            handlePlacement(mc, pose, mainHandPos, offHandPos, mainSpeed, offSpeed);
        }
    }

    private static void handlePickup(Minecraft mc, PlayerPoseClient pose, Vec3 mainHandPos, Vec3 offHandPos, double mainSpeed, double offSpeed) {
        if (!mc.player.getMainHandItem().isEmpty() || !mc.player.getOffhandItem().isEmpty() || actionCooldown > 0) {
            resetPickup();
            return;
        }

        BlockPos mainTarget = findNearbyCarryBlock(mc, mainHandPos);
        BlockPos offTarget = findNearbyCarryBlock(mc, offHandPos);
        if (mainTarget == null || offTarget == null || !mainTarget.equals(offTarget)) {
            resetPickup();
            return;
        }

        if (mainSpeed > MAX_HAND_SPEED || offSpeed > MAX_HAND_SPEED) {
            resetPickup();
            return;
        }

        Vec3 midpoint = midpoint(mainHandPos, offHandPos);
        pickupTicks++;
        pulseProgress(mc, midpoint, pickupTicks);

        if (pickupTicks >= PICKUP_HOLD_TICKS && bridge != null) {
            bridge.sendPickupBlock(mainTarget);
            actionCooldown = 10;
            resetPickup();
            VisorAPI.client().getInputManager().triggerHapticPulse(HandType.MAIN, 320f, 1.0f, 0.18f);
            VisorAPI.client().getInputManager().triggerHapticPulse(HandType.OFFHAND, 320f, 1.0f, 0.18f);
            mc.player.playSound(SoundEvents.ITEM_PICKUP, 1.0f, 0.9f);
        }
    }

    private static void handlePlacement(Minecraft mc, PlayerPoseClient pose, Vec3 mainHandPos, Vec3 offHandPos, double mainSpeed, double offSpeed) {
        if (!mc.player.getMainHandItem().isEmpty() || !mc.player.getOffhandItem().isEmpty() || actionCooldown > 0) {
            resetPlace();
            return;
        }

        if (mainSpeed > MAX_HAND_SPEED || offSpeed > MAX_HAND_SPEED) {
            resetPlace();
            return;
        }

        Vec3 midpoint = midpoint(mainHandPos, offHandPos);
        BlockHitResult hitResult = raycastToFloor(mc, midpoint);
        if (hitResult.getType() != HitResult.Type.BLOCK || hitResult.getDirection() != Direction.UP) {
            resetPlace();
            return;
        }

        double heightAboveFloor = midpoint.y - hitResult.getLocation().y;
        if (heightAboveFloor < 0.05D || heightAboveFloor > MAX_PLACE_HEIGHT) {
            resetPlace();
            return;
        }

        BlockPos placePos = getPlacementPos(mc, hitResult);
        if (placePos == null || wouldPlaceInsidePlayer(mc, placePos)) {
            resetPlace();
            return;
        }

        placeTicks++;
        pulseProgress(mc, hitResult.getLocation(), placeTicks);

        if (placeTicks >= PLACE_HOLD_TICKS && bridge != null) {
            bridge.sendPlaceBlock(hitResult.getBlockPos(), hitResult.getDirection());
            actionCooldown = 10;
            resetPlace();
            VisorAPI.client().getInputManager().triggerHapticPulse(HandType.MAIN, 320f, 1.0f, 0.18f);
            VisorAPI.client().getInputManager().triggerHapticPulse(HandType.OFFHAND, 320f, 1.0f, 0.18f);
            mc.player.playSound(SoundEvents.STONE_PLACE, 0.8f, 1.0f);
        }
    }

    private static void pulseProgress(Minecraft mc, Vec3 pos, int ticks) {
        if (ticks % 4 == 0) {
            VisorAPI.client().getInputManager().triggerHapticPulse(HandType.MAIN, 90f, 0.15f, 0.05f);
            VisorAPI.client().getInputManager().triggerHapticPulse(HandType.OFFHAND, 90f, 0.15f, 0.05f);
            mc.level.addParticle(ParticleTypes.ENCHANT, pos.x, pos.y + 0.1D, pos.z, 0.0D, 0.03D, 0.0D);
        }
    }

    private static BlockPos findNearbyCarryBlock(Minecraft mc, Vec3 handPos) {
        BlockPos center = BlockPos.containing(handPos);
        BlockPos bestPos = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    if (!VRCarryBlockHandler.isSupportedCarryBlock(mc.level, pos)) {
                        continue;
                    }

                    BlockState state = mc.level.getBlockState(pos);
                    var shape = state.getShape(mc.level, pos);
                    AABB bounds = shape.bounds();
                    if (shape.isEmpty()) {
                        bounds = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
                    }
                    AABB worldBounds = bounds.move(pos);
                    Vec3 closest = new Vec3(
                        clamp(handPos.x, worldBounds.minX, worldBounds.maxX),
                        clamp(handPos.y, worldBounds.minY, worldBounds.maxY),
                        clamp(handPos.z, worldBounds.minZ, worldBounds.maxZ)
                    );

                    double distance = handPos.distanceTo(closest);
                    if (distance <= PICKUP_REACH && distance < bestDistance) {
                        bestDistance = distance;
                        bestPos = pos;
                    }
                }
            }
        }

        return bestPos;
    }

    private static BlockHitResult raycastToFloor(Minecraft mc, Vec3 midpoint) {
        Vec3 start = midpoint.add(0.0D, 0.15D, 0.0D);
        Vec3 end = midpoint.add(0.0D, -0.75D, 0.0D);
        return mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
    }

    private static void updateCarriedDisplay(Minecraft mc, PlayerPoseClient pose) {
        VRCarryData carryData = VRCarryDataManager.getCarryData(mc.player);
        if (!carryData.isCarrying(VRCarryData.CarryType.BLOCK)) {
            removeDisplay(mc);
            return;
        }

        if (carriedDisplay == null || !carriedDisplay.isAlive()) {
            carriedDisplay = EntityType.ITEM_DISPLAY.create(mc.level);
            if (carriedDisplay == null) {
                return;
            }
            carriedDisplay.setNoGravity(true);
            carriedDisplay.setInvulnerable(true);
            mc.level.addFreshEntity(carriedDisplay);
            renderedState = null;
        }

        BlockState state = carryData.getBlockState();
        if (renderedState == null || renderedState != state) {
            ItemStack renderStack = new ItemStack(state.getBlock());
            CompoundTag tag = new CompoundTag();
            carriedDisplay.saveWithoutId(tag);
            tag.put("item", renderStack.save(new CompoundTag()));
            tag.putString("item_display", "fixed");
            tag.putFloat("view_range", 6.0F);
            tag.putFloat("width", 2.5F);
            tag.putFloat("height", 2.5F);
            tag.putFloat("shadow_radius", 0.0F);
            tag.putFloat("shadow_strength", 0.0F);

            CompoundTag transform = new CompoundTag();
            ListTag scale = new ListTag();
            scale.add(FloatTag.valueOf(2.25F));
            scale.add(FloatTag.valueOf(2.25F));
            scale.add(FloatTag.valueOf(2.25F));
            transform.put("scale", scale);
            tag.put("transformation", transform);

            carriedDisplay.load(tag);
            renderedState = state;
        }

        Vec3 mainHandPos = getGrabPoint(pose.getMainHand());
        Vec3 offHandPos = getGrabPoint(pose.getOffhand());
        Vec3 midpoint = midpoint(mainHandPos, offHandPos);
        Vec3 handDirection = offHandPos.subtract(mainHandPos);
        if (handDirection.lengthSqr() < 1.0E-6D) {
            handDirection = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            handDirection = handDirection.normalize();
        }
        Vec3 playerForward = mc.player.getLookAngle().multiply(1.0D, 0.0D, 1.0D);
        if (playerForward.lengthSqr() < 1.0E-6D) {
            playerForward = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            playerForward = playerForward.normalize();
        }
        Vec3 displayPos = midpoint.add(playerForward.scale(0.22D)).add(0.0D, 0.02D, 0.0D);

        carriedDisplay.setPos(displayPos);
        carriedDisplay.setXRot(15.0F);
        carriedDisplay.setYRot((float) Math.toDegrees(Math.atan2(handDirection.x, handDirection.z)));
    }

    private static void removeDisplay(Minecraft mc) {
        if (carriedDisplay != null) {
            carriedDisplay.discard();
            carriedDisplay = null;
        }
        renderedState = null;
    }

    private static BlockPos getPlacementPos(Minecraft mc, BlockHitResult hitResult) {
        BlockPos pos = hitResult.getBlockPos();
        if (!mc.level.getBlockState(pos).canBeReplaced()) {
            pos = pos.relative(hitResult.getDirection());
        }
        return pos;
    }

    private static boolean wouldPlaceInsidePlayer(Minecraft mc, BlockPos placePos) {
        VRCarryData carryData = VRCarryDataManager.getCarryData(mc.player);
        BlockState carriedState = carryData.getBlockState();
        AABB shapeBox = carriedState.getShape(mc.level, placePos).bounds();
        if (shapeBox.getXsize() <= 0.0D || shapeBox.getYsize() <= 0.0D || shapeBox.getZsize() <= 0.0D) {
            shapeBox = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);
        }

        AABB placeBox = shapeBox.move(placePos);
        AABB playerBox = mc.player.getBoundingBox().inflate(-0.05D);
        return placeBox.intersects(playerBox) || placePos.equals(mc.player.blockPosition()) || placePos.equals(mc.player.blockPosition().below());
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static Vec3 poseToVec(VRPose pose) {
        return new Vec3(pose.getPosition().x(), pose.getPosition().y(), pose.getPosition().z());
    }

    private static Vec3 getGrabPoint(VRPose pose) {
        Vector3f offset = new Vector3f(0.0f, 0.03f, -0.10f);
        Vector3f point = pose.getCustomVector(offset).add(pose.getPosition());
        return new Vec3(point.x(), point.y(), point.z());
    }

    private static Vec3 midpoint(Vec3 a, Vec3 b) {
        return new Vec3((a.x + b.x) * 0.5D, (a.y + b.y) * 0.5D, (a.z + b.z) * 0.5D);
    }

    private static void resetPickup() {
        pickupTicks = 0;
    }

    private static void resetPlace() {
        placeTicks = 0;
    }
}
