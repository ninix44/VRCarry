package org.vmstudio.vrcarry.forge;

import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.vrcarry.core.client.ExampleAddonClient;
import org.vmstudio.vrcarry.core.common.VisorExample;
import org.vmstudio.vrcarry.core.server.VRCarryAddonServer;
import net.minecraftforge.fml.common.Mod;

@Mod(VisorExample.MOD_ID)
public class ExampleMod {
    public ExampleMod() {
        if (ModLoader.get().isDedicatedServer()) {
            VisorAPI.registerAddon(
                    new VRCarryAddonServer()
            );
        } else {
            VisorAPI.registerAddon(
                    new ExampleAddonClient()
            );
        }
    }
}
