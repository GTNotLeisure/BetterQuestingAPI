package com.hfstudio.bqapi.mixins.late.BetterQuesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.google.common.collect.ImmutableSet;
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.runtime.BQManagedQuestProgress;
import com.hfstudio.bqapi.runtime.BQRuntime;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.commands.admin.QuestCommandDefaults;
import betterquesting.network.handlers.NetChapterSync;
import betterquesting.network.handlers.NetSettingSync;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import cpw.mods.fml.common.FMLCommonHandler;

@Mixin(value = QuestCommandDefaults.class, remap = false)
public abstract class MixinQuestCommandDefaults {

    @Redirect(
        method = "save(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;)V",
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/questing/QuestLineDatabase;getOrderedEntries()Ljava/util/List;"))
    private static List<Map.Entry<UUID, IQuestLine>> hfstudio$bqapi$filterQuestLinesForSave(
        QuestLineDatabase instance) {
        Set<UUID> filteredChapterUuids = BQApi.getSaveFilteredChapterUuids();
        if (filteredChapterUuids.isEmpty()) {
            return instance.getOrderedEntries();
        }
        return instance.getOrderedEntries()
            .stream()
            .filter(entry -> !filteredChapterUuids.contains(entry.getKey()))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    @Redirect(
        method = "save(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;)V",
        at = @At(value = "INVOKE", target = "Lbetterquesting/questing/QuestDatabase;entrySet()Ljava/util/Set;"))
    private static Set<Map.Entry<UUID, IQuest>> hfstudio$bqapi$filterQuestsForSave(QuestDatabase instance) {
        Set<UUID> filteredQuestUuids = BQApi.getSaveFilteredQuestUuids();
        if (filteredQuestUuids.isEmpty()) {
            return instance.entrySet();
        }
        return instance.entrySet()
            .stream()
            .filter(entry -> !filteredQuestUuids.contains(entry.getKey()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Redirect(
        method = "saveLegacy(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;)V",
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/questing/QuestDatabase;writeToNBT(Lnet/minecraft/nbt/NBTTagList;Ljava/util/List;)Lnet/minecraft/nbt/NBTTagList;"))
    private static NBTTagList hfstudio$bqapi$filterQuestNbtForSaveLegacy(QuestDatabase instance, NBTTagList nbt,
        List<UUID> subset) {
        return instance.writeToNBT(nbt, filteredSubset(subset, BQApi.getSaveFilteredQuestUuids(), instance.keySet()));
    }

    @Redirect(
        method = "saveLegacy(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;)V",
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/questing/QuestLineDatabase;writeToNBT(Lnet/minecraft/nbt/NBTTagList;Ljava/util/List;)Lnet/minecraft/nbt/NBTTagList;"))
    private static NBTTagList hfstudio$bqapi$filterQuestLineNbtForSaveLegacy(QuestLineDatabase instance, NBTTagList nbt,
        List<UUID> subset) {
        return instance.writeToNBT(nbt, filteredSubset(subset, BQApi.getSaveFilteredChapterUuids(), instance.keySet()));
    }

    @Redirect(
        method = { "load", "loadLegacy" },
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/questing/QuestDatabase;readProgressFromNBT(Lnet/minecraft/nbt/NBTTagList;Z)V"))
    private static void hfstudio$bqapi$captureManagedProgressOnLoad(QuestDatabase instance, NBTTagList progressList,
        boolean merge) {
        BQManagedQuestProgress.capture(progressList, merge);
        instance.readProgressFromNBT(progressList, merge);
    }

    @Redirect(
        method = { "load", "loadLegacy" },
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/network/handlers/NetSettingSync;sendSync(Lnet/minecraft/entity/player/EntityPlayerMP;)V"))
    private static void hfstudio$bqapi$syncSettingsToCurrentPlayers(EntityPlayerMP player) {
        MinecraftServer server = hfstudio$bqapi$server();
        BQRuntime runtime = BQApi.getRuntime();
        runtime.afterDatabaseLoad(server);
        if (player != null) {
            NetSettingSync.sendSync(player);
        } else {
            runtime.getSynchronizer()
                .syncSettings(server);
        }
    }

    @Redirect(
        method = { "load", "loadLegacy" },
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/network/handlers/NetQuestSync;quickSync(Ljava/util/UUID;ZZ)V"))
    private static void hfstudio$bqapi$syncQuestsToCurrentPlayers(UUID questUuid, boolean config, boolean progress) {
        BQApi.getRuntime()
            .getSynchronizer()
            .syncQuests(hfstudio$bqapi$server(), questUuid, config, progress);
    }

    @Redirect(
        method = { "load", "loadLegacy" },
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/network/handlers/NetChapterSync;sendSync(Lnet/minecraft/entity/player/EntityPlayerMP;Ljava/util/Collection;)V"))
    private static void hfstudio$bqapi$syncChaptersToCurrentPlayers(EntityPlayerMP player, Collection<UUID> chapters) {
        if (player != null) {
            NetChapterSync.sendSync(player, chapters);
        } else {
            BQApi.getRuntime()
                .getSynchronizer()
                .syncChapters(hfstudio$bqapi$server(), chapters);
        }
    }

    @Unique
    private static MinecraftServer hfstudio$bqapi$server() {
        return FMLCommonHandler.instance()
            .getMinecraftServerInstance();
    }

    @Unique
    private static List<UUID> filteredSubset(List<UUID> subset, Set<UUID> managedUuids, Set<UUID> allAvailable) {
        if (managedUuids.isEmpty()) {
            return subset;
        }
        ImmutableSet.Builder<UUID> result = ImmutableSet.builder();
        for (UUID uuid : subset == null ? allAvailable : subset) {
            if (!managedUuids.contains(uuid)) {
                result.add(uuid);
            }
        }
        return result.build()
            .asList();
    }
}
