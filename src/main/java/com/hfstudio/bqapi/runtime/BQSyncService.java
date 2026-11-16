package com.hfstudio.bqapi.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import betterquesting.network.handlers.NetChapterSync;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.network.handlers.NetSettingSync;

public class BQSyncService {

    public void syncManaged(MinecraftServer server, BQApplyResult result) {
        if (result.isEmpty()) {
            return;
        }
        for (EntityPlayerMP player : getRemotePlayers(server)) {
            NetQuestSync.sendSync(player, result.questUuids(), true, true, true);
            NetChapterSync.sendSync(player, result.chapterUuids());
        }
    }

    /**
     * A null quest UUID requests a full quest sync.
     */
    public void syncQuests(MinecraftServer server, UUID questUuid, boolean config, boolean progress) {
        Collection<UUID> questUuids = questUuid == null ? null : Collections.singletonList(questUuid);
        for (EntityPlayerMP player : getRemotePlayers(server)) {
            NetQuestSync.sendSync(player, questUuids, config, progress, true);
        }
    }

    public void syncChapters(MinecraftServer server, Collection<UUID> chapterUuids) {
        Collection<UUID> scope = chapterUuids == null ? null : List.copyOf(chapterUuids);
        for (EntityPlayerMP player : getRemotePlayers(server)) {
            NetChapterSync.sendSync(player, scope);
        }
    }

    public void syncSettings(MinecraftServer server) {
        for (EntityPlayerMP player : getRemotePlayers(server)) {
            NetSettingSync.sendSync(player);
        }
    }

    protected List<EntityPlayerMP> getRemotePlayers(MinecraftServer server) {
        if (server == null || server.getConfigurationManager() == null) {
            return Collections.emptyList();
        }
        List<EntityPlayerMP> players = new ArrayList<>(server.getConfigurationManager().playerEntityList);
        if (!server.isDedicatedServer()) {
            // The integrated host shares the server database, so exclude it from client synchronization.
            players.removeIf(
                player -> Objects.equals(
                    server.getServerOwner(),
                    player.getGameProfile()
                        .getName()));
        }
        return players;
    }
}
