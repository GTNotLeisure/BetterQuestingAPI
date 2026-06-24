package com.hfstudio.bqapi.mixins.late.BetterQuesting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.nbt.NBTTagList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.runtime.BQManagedQuestProgress;

import betterquesting.handlers.SaveLoadHandler;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;

@Mixin(value = SaveLoadHandler.class, remap = false)
public abstract class MixinSaveLoadHandler {

    @Inject(method = "loadProgress()V", at = @At("HEAD"))
    private void hfstudio$bqapi$resetManagedProgressCapture(CallbackInfo ci) {
        BQManagedQuestProgress.reset();
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

    private static List<UUID> filteredSubset(List<UUID> subset, Set<UUID> managedUuids, Set<UUID> allAvailable) {
        if (managedUuids.isEmpty()) {
            return subset;
        }
        if (subset == null) {
            return allAvailable.stream()
                .filter(id -> !managedUuids.contains(id))
                .collect(Collectors.toCollection(ArrayList::new));
        }
        return subset.stream()
            .filter(id -> !managedUuids.contains(id))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    @Redirect(
        method = "loadProgress()V",
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/questing/QuestDatabase;readProgressFromNBT(Lnet/minecraft/nbt/NBTTagList;Z)V"))
    private void hfstudio$bqapi$captureManagedProgressOnLoad(QuestDatabase instance, NBTTagList progressList,
        boolean merge) {
        BQManagedQuestProgress.capture(progressList, merge);
        instance.readProgressFromNBT(progressList, merge);
    }
}
