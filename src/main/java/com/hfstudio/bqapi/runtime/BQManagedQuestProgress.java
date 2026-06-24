package com.hfstudio.bqapi.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import com.hfstudio.bqapi.BQApi;

import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.UuidConverter;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestInstance;

public class BQManagedQuestProgress {

    private static final List<ProgressBatch> pendingBatches = new ArrayList<>();

    private BQManagedQuestProgress() {}

    public static synchronized void reset() {
        pendingBatches.clear();
    }

    public static synchronized void capture(NBTTagList progressList, boolean merge) {
        if (progressList == null || progressList.tagCount() <= 0) {
            return;
        }
        NBTTagList capturedProgress = new NBTTagList();
        int count = progressList.tagCount();
        for (int i = 0; i < count; i++) {
            NBTTagCompound questProgress = progressList.getCompoundTagAt(i);
            Optional<UUID> questUuid = readQuestUuid(questProgress);
            if (!questUuid.isPresent()) {
                continue;
            }
            capturedProgress.appendTag((NBTBase) questProgress.copy());
        }
        if (capturedProgress.tagCount() <= 0) {
            return;
        }
        pendingBatches.add(new ProgressBatch(capturedProgress, merge));
    }

    public static synchronized void replay() {
        if (pendingBatches.isEmpty()) {
            return;
        }
        Set<UUID> managedQuestUuids = BQApi.getManagedQuestUuids();
        if (managedQuestUuids.isEmpty()) {
            pendingBatches.clear();
            return;
        }

        for (ProgressBatch batch : pendingBatches) {
            NBTTagList replayableProgress = new NBTTagList();
            Set<UUID> affectedUsers = new LinkedHashSet<>();
            int count = batch.progressList.tagCount();
            for (int i = 0; i < count; i++) {
                NBTTagCompound questProgress = batch.progressList.getCompoundTagAt(i);
                Optional<UUID> questUuid = readQuestUuid(questProgress);
                if (!questUuid.isPresent() || !managedQuestUuids.contains(questUuid.get())) {
                    continue;
                }
                collectUsersBeforeReplay(questUuid.get(), affectedUsers);
                replayableProgress.appendTag((NBTBase) questProgress.copy());
                collectUsers(questProgress, affectedUsers);
            }

            if (replayableProgress.tagCount() <= 0) {
                continue;
            }

            QuestDatabase.INSTANCE.readProgressFromNBT(replayableProgress, batch.merge);
            collectUsersAfterReplay(replayableProgress, affectedUsers);
            if (!affectedUsers.isEmpty()) {
                SaveLoadHandler.INSTANCE.addDirtyPlayers(affectedUsers);
            }
        }

        pendingBatches.clear();
    }

    private static Optional<UUID> readQuestUuid(NBTTagCompound questProgress) {
        Optional<UUID> questUuid = NBTConverter.UuidValueType.QUEST.tryReadId(questProgress);
        if (questUuid.isPresent()) {
            return questUuid;
        }
        if (questProgress.hasKey("questID", 99)) {
            return Optional.of(UuidConverter.convertLegacyId(questProgress.getInteger("questID")));
        }
        return Optional.empty();
    }

    private static void collectUsers(NBTTagCompound questProgress, Set<UUID> affectedUsers) {
        collectCompletionUsers(questProgress, affectedUsers);
        collectTaskUsers(questProgress, affectedUsers);
    }

    private static void collectCompletionUsers(NBTTagCompound questProgress, Set<UUID> affectedUsers) {
        NBTTagList completed = questProgress.getTagList("completed", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < completed.tagCount(); i++) {
            addUuid(
                completed.getCompoundTagAt(i)
                    .getString("uuid"),
                affectedUsers);
        }
    }

    private static void collectTaskUsers(NBTTagCompound questProgress, Set<UUID> affectedUsers) {
        NBTTagList tasks = questProgress.getTagList("tasks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tasks.tagCount(); i++) {
            collectTaskCompleteUsers(tasks.getCompoundTagAt(i), affectedUsers);
            collectTaskProgressUsers(tasks.getCompoundTagAt(i), affectedUsers);
        }
    }

    private static void collectTaskCompleteUsers(NBTTagCompound taskProgress, Set<UUID> affectedUsers) {
        NBTTagList completeUsers = taskProgress.getTagList("completeUsers", Constants.NBT.TAG_STRING);
        for (NBTBase tag : NBTConverter.getTagList(completeUsers)) {
            if (tag instanceof NBTTagString) {
                addUuid(((NBTTagString) tag).func_150285_a_(), affectedUsers);
            }
        }
    }

    private static void collectTaskProgressUsers(NBTTagCompound taskProgress, Set<UUID> affectedUsers) {
        NBTTagList userProgress = taskProgress.getTagList("userProgress", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < userProgress.tagCount(); i++) {
            addUuid(
                userProgress.getCompoundTagAt(i)
                    .getString("uuid"),
                affectedUsers);
        }
    }

    private static void collectUsersBeforeReplay(UUID questUuid, Set<UUID> affectedUsers) {
        collectQuestCompletionUsers(QuestDatabase.INSTANCE.get(questUuid), affectedUsers);
    }

    private static void collectUsersAfterReplay(NBTTagList replayedProgress, Set<UUID> affectedUsers) {
        for (int i = 0; i < replayedProgress.tagCount(); i++) {
            Optional<UUID> questUuid = readQuestUuid(replayedProgress.getCompoundTagAt(i));
            if (!questUuid.isPresent()) {
                continue;
            }
            collectQuestCompletionUsers(QuestDatabase.INSTANCE.get(questUuid.get()), affectedUsers);
        }
    }

    private static void collectQuestCompletionUsers(IQuest quest, Set<UUID> affectedUsers) {
        if (quest instanceof QuestInstance) {
            ((QuestInstance) quest).getUsersWithCompletionData(affectedUsers);
        }
    }

    private static void addUuid(String value, Set<UUID> affectedUsers) {
        if (value == null || value.isEmpty()) {
            return;
        }
        try {
            affectedUsers.add(UUID.fromString(value));
        } catch (IllegalArgumentException ignored) {}
    }

    private static class ProgressBatch {

        private final NBTTagList progressList;
        private final boolean merge;

        private ProgressBatch(NBTTagList progressList, boolean merge) {
            this.progressList = progressList;
            this.merge = merge;
        }
    }
}
