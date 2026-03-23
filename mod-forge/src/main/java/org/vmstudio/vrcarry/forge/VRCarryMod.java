package org.vmstudio.vrcarry.forge;

import org.vmstudio.visor.api.ModLoader;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.vrcarry.core.client.VRCarryAddonClient;
import org.vmstudio.vrcarry.core.common.VRCarry;
import org.vmstudio.vrcarry.core.server.VRCarryAddonServer;
import net.minecraftforge.fml.common.Mod;

@Mod(VRCarry.MOD_ID)
public class VRCarryMod {
    public VRCarryMod() {
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
