package com.hfstudio.bqapi.mixins.late.BetterQuesting;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTTagList;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.runtime.BQManagedQuestProgress;
import com.hfstudio.bqapi.runtime.BQReinjector;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.commands.admin.QuestCommandDefaults;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import cpw.mods.fml.common.FMLCommonHandler;

@Mixin(value = QuestCommandDefaults.class, remap = false)
public abstract class MixinQuestCommandDefaults {

    @Inject(method = "load(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;Z)V", at = @At("HEAD"))
    private static void hfstudio$bqapi$resetManagedProgressCaptureBeforeLoad(ICommandSender sender, String databaseName,
        File dataDir, boolean loadWorldSettings, CallbackInfo ci) {
        BQManagedQuestProgress.reset();
    }

    @Inject(
        method = "loadLegacy(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;Z)V",
        at = @At("HEAD"))
    private static void hfstudio$bqapi$resetManagedProgressCaptureBeforeLoadLegacy(ICommandSender sender,
        String databaseName, File legacyFile, boolean loadWorldSettings, CallbackInfo ci) {
        BQManagedQuestProgress.reset();
    }

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
        method = "load(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/questing/QuestDatabase;readProgressFromNBT(Lnet/minecraft/nbt/NBTTagList;Z)V"))
    private static void hfstudio$bqapi$captureManagedProgressOnLoad(QuestDatabase instance, NBTTagList progressList,
        boolean merge) {
        BQManagedQuestProgress.capture(progressList, merge);
        instance.readProgressFromNBT(progressList, merge);
    }

    @Redirect(
        method = "loadLegacy(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;Z)V",
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/questing/QuestDatabase;readProgressFromNBT(Lnet/minecraft/nbt/NBTTagList;Z)V"))
    private static void hfstudio$bqapi$captureManagedProgressOnLoadLegacy(QuestDatabase instance,
        NBTTagList progressList, boolean merge) {
        BQManagedQuestProgress.capture(progressList, merge);
        instance.readProgressFromNBT(progressList, merge);
    }

    @Inject(method = "load(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;Z)V", at = @At("TAIL"))
    private static void hfstudio$bqapi$afterLoad(ICommandSender sender, String databaseName, File dataDir,
        boolean loadWorldSettings, CallbackInfo ci) {
        BQReinjector.reinject(
            FMLCommonHandler.instance()
                .getMinecraftServerInstance());
    }

    @Inject(
        method = "loadLegacy(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;Z)V",
        at = @At("TAIL"))
    private static void hfstudio$bqapi$afterLoadLegacy(ICommandSender sender, String databaseName, File legacyFile,
        boolean loadWorldSettings, CallbackInfo ci) {
        BQReinjector.reinject(
            FMLCommonHandler.instance()
                .getMinecraftServerInstance());
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
}
