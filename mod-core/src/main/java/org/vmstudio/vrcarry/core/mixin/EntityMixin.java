package org.vmstudio.vrcarry.core.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.vrcarry.core.common.VRCarryBlockHandler;

@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "stopRiding", at = @At("HEAD"), cancellable = true)
    private void onStopRiding(CallbackInfo ci) {
        if (VRCarryBlockHandler.isLockedBedPassenger((Entity) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "positionRider(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/entity/Entity$MoveFunction;)V", at = @At("HEAD"), cancellable = true)
    private void onPositionRider(Entity passenger, Entity.MoveFunction moveFunction, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player player) || !VRCarryBlockHandler.shouldOverrideBedPassengerPosition(player, passenger)) {
            return;
        }

        var offset = VRCarryBlockHandler.getBedPassengerOffset(player);
        moveFunction.accept(
            passenger,
            self.getX() + offset.x,
            self.getY() + offset.y,
            self.getZ() + offset.z
        );
        var bedFacing = VRCarryBlockHandler.getLockedBedFacing(passenger);
        float bedYaw = bedFacing != null ? VRCarryBlockHandler.getBedYaw(bedFacing) : self.getYRot();
        passenger.setYRot(bedYaw);
        passenger.setYHeadRot(bedYaw);
        passenger.setYBodyRot(bedYaw);
        ci.cancel();
    }
}
