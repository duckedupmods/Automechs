package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.menu.MechDatabaseMenu;
import com.duckedupmods.automechs.network.MechListPayload;

import net.minecraft.client.Minecraft;

/**
 * Client-only receiver for the Mech Database list. Kept in its own class (referenced only from a
 * nested lambda in the network registration) so it is never classloaded on a dedicated server.
 */
public final class MechDatabaseClient {

    private MechDatabaseClient() {}

    public static void receive(MechListPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof MechDatabaseMenu menu) {
            menu.setData(payload.mechs(), payload.folders());
        }
    }
}
