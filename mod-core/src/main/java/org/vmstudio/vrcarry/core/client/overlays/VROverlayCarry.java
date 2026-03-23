package org.vmstudio.vrcarry.core.client.overlays;


import org.vmstudio.visor.api.client.gui.overlays.framework.VROverlayScreen;
import org.vmstudio.visor.api.common.addon.VisorAddon;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.NotNull;

public class VROverlayCarry extends VROverlayScreen {
    public static final String ID = "carry";


    public VROverlayCarry(@NotNull VisorAddon owner, @NotNull String id) {
        super(owner, id);
        //if you want it to be enabled once created
        setEnabled(true);
    }

    @Override
    protected void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        //empty
    }

    @Override
    protected boolean updateVisibility() {
        return false; //never display
    }

    @Override
    public void onUpdatePose(float v) {

    }
}
