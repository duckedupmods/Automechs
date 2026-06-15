package com.duckedupmods.automechs.client;

import com.duckedupmods.automechs.menu.TerminalMenu;
import com.duckedupmods.automechs.network.StorageSnapshotPayload;

import net.minecraft.client.Minecraft;

/**
 * Client-only receiver for {@link StorageSnapshotPayload}. Pushes the freshly-arrived network snapshot
 * onto the open {@link TerminalMenu} so the Storage Terminal screen redraws with current data. Isolated in
 * its own class so the dedicated server never loads client types.
 */
public final class TerminalClient {

    private TerminalClient() {}

    public static void receive(StorageSnapshotPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.containerMenu instanceof TerminalMenu menu) {
            menu.setSnapshot(payload.items(), payload.online(), payload.itemCount(),
                    payload.rackCount(), payload.storedFe());
        }
    }
}
