package org.vmstudio.vrcarry.core.server;

import org.vmstudio.visor.api.common.addon.VisorAddon;
import org.vmstudio.vrcarry.core.common.VRCarry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VRCarryAddonServer implements VisorAddon {
    @Override
    public void onAddonLoad() {

    }

    @Override
    public @Nullable String getAddonPackagePath() {
        return "org.vmstudio.vrcarry.core.server";
    }

    @Override
    public @NotNull String getAddonId() {
        return VRCarry.MOD_ID;
    }

    @Override
    public @NotNull Component getAddonName() {
        return Component.literal(VRCarry.MOD_NAME);
    }

    @Override
    public String getModId() {
        return VRCarry.MOD_ID;
    }
}
