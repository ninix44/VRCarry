package org.vmstudio.vrcarry.core.client;

import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.common.addon.VisorAddon;
import org.vmstudio.vrcarry.core.client.overlays.VROverlayCarry;
import org.vmstudio.vrcarry.core.common.VRCarry;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class VRCarryAddonClient implements VisorAddon {
    @Override
    public void onAddonLoad() {
        VisorAPI.addonManager().getRegistries()
                .overlays()
                .registerComponents(
                        List.of(
                                new VROverlayCarry(
                                        this,
                                        VROverlayCarry.ID
                                )
                        )
                );
    }

    @Override
    public @Nullable String getAddonPackagePath() {
        return "org.vmstudio.vrcarry.core.client";
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
