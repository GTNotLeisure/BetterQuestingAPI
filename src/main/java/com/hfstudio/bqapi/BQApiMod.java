package com.hfstudio.bqapi;

import com.hfstudio.bqapi.command.BQApiCommand;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

@Mod(
    modid = BQApiMod.MODID,
    name = "BetterQuesting Runtime API",
    version = BQApiMod.VERSION,
    dependencies = "required-after:betterquesting;",
    acceptableRemoteVersions = "*",
    acceptedMinecraftVersions = "1.7.10")
public class BQApiMod {

    public static final String MODID = Tags.MODID;
    public static final String VERSION = Tags.VERSION;

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new BQApiCommand());
    }

}
