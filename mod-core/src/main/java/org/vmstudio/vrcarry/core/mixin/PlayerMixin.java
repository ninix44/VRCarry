package org.vmstudio.vrcarry.core.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.vrcarry.core.common.VRCarryBlockHandler;
import org.vmstudio.vrcarry.core.common.VRCarryData;
import org.vmstudio.vrcarry.core.common.VRCarryDataManager;

@Mixin(Player.class)
public abstract class PlayerMixin extends LivingEntity implements VRCarryDataManager.ICarrying {

    @Unique
    private static final EntityDataAccessor<CompoundTag> VR_CARRY_DATA_KEY =
        SynchedEntityData.defineId(Player.class, EntityDataSerializers.COMPOUND_TAG);

    protected PlayerMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public void setVRCarryData(VRCarryData data) {
        CompoundTag nbt = data.getNbt();
        nbt.putInt("tick", tickCount);
        getEntityData().set(VR_CARRY_DATA_KEY, nbt);
    }

    @Override
    public VRCarryData getVRCarryData() {
        return new VRCarryData(getEntityData().get(VR_CARRY_DATA_KEY).copy());
    }

    @Inject(method = "defineSynchedData", at = @At("RETURN"))
    private void onDefineSynchedData(CallbackInfo ci) {
        getEntityData().define(VR_CARRY_DATA_KEY, new CompoundTag());
    }

    @Inject(method = "addAdditionalSaveData", at = @At("RETURN"))
    private void onAddAdditionalSaveData(CompoundTag tag, CallbackInfo ci) {
        tag.put("VRCarryData", getVRCarryData().getNbt());
    }

    @Inject(method = "readAdditionalSaveData", at = @At("RETURN"))
    private void onReadAdditionalSaveData(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("VRCarryData")) {
            setVRCarryData(new VRCarryData(tag.getCompound("VRCarryData")));
        }
    }

    @Inject(method = "stopSleepInBed", at = @At("HEAD"), cancellable = true)
    private void onStopSleepInBed(boolean skipSleepTimer, boolean updateSleepingPlayers, CallbackInfo ci) {
        if (VRCarryBlockHandler.isLockedBedPassenger((Player) (Object) this)) {
            ci.cancel();
        }
    }
}
