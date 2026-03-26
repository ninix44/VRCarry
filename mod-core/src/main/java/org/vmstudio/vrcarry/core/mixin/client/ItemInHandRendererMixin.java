package org.vmstudio.vrcarry.core.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.vrcarry.core.client.VRCarryLogic;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Shadow
    public abstract void renderItem(LivingEntity entity, ItemStack itemStack, ItemDisplayContext displayContext, boolean leftHand, PoseStack poseStack, MultiBufferSource multiBufferSource, int light);

    @Inject(method = "renderHandsWithItems", at = @At("TAIL"))
    private void renderVRCarryBlock(float partialTick, PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, LocalPlayer player, int light, CallbackInfo ci) {
        if (!VRCarryLogic.shouldRenderFirstPersonCarry(player)) {
            return;
        }

        ItemStack renderStack = VRCarryLogic.getFirstPersonRenderStack(player);
        if (renderStack.isEmpty()) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.0D, -0.35D, -0.90D);
        poseStack.mulPose(Axis.YP.rotationDegrees(VRCarryLogic.getFirstPersonCarryYaw(player)));
        poseStack.mulPose(Axis.XP.rotationDegrees(12.0F));
        poseStack.scale(2.6F, 2.6F, 2.6F);
        renderItem(player, renderStack, ItemDisplayContext.FIXED, false, poseStack, bufferSource, light);
        poseStack.popPose();
    }
}
