package org.vmstudio.vrcarry.core.common;

import net.minecraft.world.entity.player.Player;

public class VRCarryDataManager {

    public static VRCarryData getCarryData(Player player) {
        return ((ICarrying) player).getVRCarryData();
    }

    public static void setCarryData(Player player, VRCarryData data) {
        ((ICarrying) player).setVRCarryData(data);
    }

    public interface ICarrying {
        void setVRCarryData(VRCarryData data);

        VRCarryData getVRCarryData();
    }
}
