package com.hfstudio.bqapi.mixins.late.BetterQuesting;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.google.common.collect.ImmutableSet;
import com.hfstudio.bqapi.BQApi;

import betterquesting.handlers.SaveLoadHandler;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;

@Mixin(value = SaveLoadHandler.class, remap = false)
public abstract class MixinSaveLoadHandler {

    @Inject(method = "loadDatabases(Lnet/minecraft/server/MinecraftServer;)V", at = @At("HEAD"))
    private void hfstudio$bqapi$beginWorldLoad(MinecraftServer server, CallbackInfo ci) {
        BQApi.getRuntime()
            .beginWorldLoad(server);
    }

    @Inject(
        method = "loadDatabases(Lnet/minecraft/server/MinecraftServer;)V",
        at = @At(value = "INVOKE", target = "Lbetterquesting/handlers/SaveLoadHandler;loadProgress()V"))
    private void hfstudio$bqapi$populateBeforeProgress(MinecraftServer server, CallbackInfo ci) {
        BQApi.getRuntime()
            .beforeWorldProgress(server);
    }

    @Inject(method = "unloadDatabases()V", at = @At("TAIL"))
    private void hfstudio$bqapi$unloadRuntime(CallbackInfo ci) {
        BQApi.getRuntime()
            .unload();
    }

    @Redirect(
        method = "saveConfig()Ljava/util/concurrent/Future;",
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/questing/QuestDatabase;writeToNBT(Lnet/minecraft/nbt/NBTTagList;Ljava/util/List;)Lnet/minecraft/nbt/NBTTagList;"))
    private NBTTagList hfstudio$bqapi$filterQuestNbtForWorldSave(QuestDatabase instance, NBTTagList nbt,
        List<UUID> subset) {
        return instance.writeToNBT(nbt, filteredSubset(subset, BQApi.getSaveFilteredQuestUuids(), instance.keySet()));
    }

    @Redirect(
        method = "saveConfig()Ljava/util/concurrent/Future;",
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/questing/QuestLineDatabase;writeToNBT(Lnet/minecraft/nbt/NBTTagList;Ljava/util/List;)Lnet/minecraft/nbt/NBTTagList;"))
    private NBTTagList hfstudio$bqapi$filterQuestLineNbtForWorldSave(QuestLineDatabase instance, NBTTagList nbt,
        List<UUID> subset) {
        return instance.writeToNBT(nbt, filteredSubset(subset, BQApi.getSaveFilteredChapterUuids(), instance.keySet()));
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
