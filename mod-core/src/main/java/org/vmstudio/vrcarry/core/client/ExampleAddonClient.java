package org.vmstudio.vrcarry.core.client;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.vmstudio.vrcarry.core.client.overlays.VROverlayExample;
import org.vmstudio.vrcarry.core.client.overlays.VROverlayTemplateExample;
import org.vmstudio.vrcarry.core.common.VisorExample;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.common.addon.VisorAddon;

import java.util.List;

public class ExampleAddonClient implements VisorAddon {
    @Override
    public void onAddonLoad() {
        VisorAPI.addonManager().getRegistries()
            .overlays()
            .registerComponents(
                List.of(
                    new VROverlayExample(
                        this,
                        VROverlayExample.ID
                    )
                                /*
                                , new VROverlayTemplateExample(
                                        this,
                                        VROverlayTemplateExample.ID
                                )
                                */
                )
            );
    }

    @Override
    public @Nullable String getAddonPackagePath() {
        return "org.vmstudio.vrcarry.core.client";
    }

    @Override
    public @NotNull String getAddonId() {
        return VisorExample.MOD_ID;
    }

    @Override
    public @NotNull Component getAddonName() {
        return Component.literal(VisorExample.MOD_NAME);
    }

    @Override
    public String getModId() {
        return VisorExample.MOD_ID;
    }
}
