package org.vmstudio.vrcarry.core.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.vrcarry.core.client.VRCarryLogic;
import org.vmstudio.vrcarry.core.common.VRCarryBlockHandler;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "scale(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;F)V", at = @At("HEAD"))
    private void onScale(LivingEntity entity, PoseStack poseStack, float partialTickTime, CallbackInfo ci) {
        float scale = VRCarryBlockHandler.isLockedBedPassenger(entity) ? VRCarryLogic.getBedPassengerScale() : 1.0F;
        if (scale != 1.0F) {
            poseStack.scale(scale, scale, scale);
        }
    }
}
