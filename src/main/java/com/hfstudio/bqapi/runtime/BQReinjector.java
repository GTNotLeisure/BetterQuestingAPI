package com.hfstudio.bqapi.runtime;

import net.minecraft.server.MinecraftServer;

import com.hfstudio.bqapi.BQApi;

public class BQReinjector {

    private BQReinjector() {}

    public static void reinject(MinecraftServer server) {
        BQApi.reinject(server);
    }
}
