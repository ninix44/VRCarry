package org.vmstudio.vrcarry.fabric;

import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.vrcarry.core.client.VRCarryAddonClient;
import org.vmstudio.vrcarry.core.server.VRCarryAddonServer;
import net.fabricmc.api.ModInitializer;

public class VRCarryMod implements ModInitializer {
    @Override
    public void onInitialize() {
        if (ModLoader.get().isDedicatedServer()) {
            VisorAPI.registerAddon(
                    new VRCarryAddonServer()
            );
        } else {
            VisorAPI.registerAddon(
                    new VRCarryAddonClient()
            );
        }
    }
}
