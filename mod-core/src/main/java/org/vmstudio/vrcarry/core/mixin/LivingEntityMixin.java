package org.vmstudio.vrcarry.core.mixin;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vmstudio.vrcarry.core.common.VRCarryBlockHandler;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = "isSleeping", at = @At("HEAD"), cancellable = true)
    private void onIsSleeping(CallbackInfoReturnable<Boolean> cir) {
        if (VRCarryBlockHandler.isLockedBedPassenger((LivingEntity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getBedOrientation", at = @At("HEAD"), cancellable = true)
    private void onGetBedOrientation(CallbackInfoReturnable<Direction> cir) {
        Direction facing = VRCarryBlockHandler.getLockedBedFacing((LivingEntity) (Object) this);
        if (facing != null) {
            cir.setReturnValue(facing);
        }
    }
}
