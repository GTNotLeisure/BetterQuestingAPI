package com.hfstudio.bqapi.runtime;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;

public class BQFileBackedOverrideResolver {

    private static final Logger LOGGER = LogManager.getLogger(BQFileBackedOverrideResolver.class);
    private static final String DEFAULT_QUESTS_DIR = "DefaultQuests";
    private static final String DEFAULT_QUESTS_FILE = "DefaultQuests.json";
    private static final String QUESTS_DIR = "Quests";
    private static final String QUEST_LINES_DIR = "QuestLines";
    private static final String QUEST_LINE_FILE = "QuestLine.json";

    public BQFileOverrideSnapshot resolve(BQDefinitionRegistry registry) {
        File defaultRoot = new File(BQ_Settings.defaultDir, DEFAULT_QUESTS_DIR);
        File defaultLegacyFile = new File(BQ_Settings.defaultDir, DEFAULT_QUESTS_FILE);
        if (!defaultRoot.isDirectory() && !defaultLegacyFile.isFile()) {
            return BQFileOverrideSnapshot.empty();
        }

        Set<UUID> fileQuestUuids = new LinkedHashSet<>();
        Set<UUID> fileChapterUuids = new LinkedHashSet<>();
        if (defaultRoot.isDirectory()) {
            fileQuestUuids.addAll(resolveQuestOverrides(new File(defaultRoot, QUESTS_DIR).toPath(), registry));
            fileChapterUuids.addAll(
                resolveChapterOverrides(new File(defaultRoot, QUEST_LINES_DIR).toPath(), registry, fileQuestUuids));
        }
        if (defaultLegacyFile.isFile()) {
            readLegacyOverrides(defaultLegacyFile, registry, fileQuestUuids, fileChapterUuids);
        }
        return new BQFileOverrideSnapshot(fileQuestUuids, fileChapterUuids);
    }

    private Set<UUID> resolveQuestOverrides(Path questRoot, BQDefinitionRegistry registry) {
        if (!Files.isDirectory(questRoot)) {
            return Collections.emptySet();
        }
        Set<UUID> importedQuestUuids = registry.getImportedQuestUuids();
        Set<UUID> resolved = new LinkedHashSet<>();
        try (Stream<Path> paths = Files.walk(questRoot)) {
            paths.filter(Files::isRegularFile)
                .filter(
                    path -> path.getFileName()
                        .toString()
                        .endsWith(".json"))
                .forEach(path -> readQuestUuid(path, importedQuestUuids, resolved));
        } catch (IOException e) {
            LOGGER.warn("Failed to scan BetterQuesting quest files at {}", questRoot, e);
        }
        return resolved;
    }

    private Set<UUID> resolveChapterOverrides(Path questLineRoot, BQDefinitionRegistry registry,
        Set<UUID> fileQuestUuids) {
        if (!Files.isDirectory(questLineRoot)) {
            return Collections.emptySet();
        }
        Set<UUID> importedChapterUuids = registry.getImportedChapterUuids();
        Set<UUID> resolved = new LinkedHashSet<>();
        File[] subdirectories = questLineRoot.toFile()
            .listFiles(File::isDirectory);
        if (subdirectories == null) {
            return resolved;
        }
        for (File subdirectory : subdirectories) {
            Path chapterFile = subdirectory.toPath()
                .resolve(QUEST_LINE_FILE);
            if (!Files.isRegularFile(chapterFile)) {
                continue;
            }
            try {
                NBTTagCompound tag = NBTConverter
                    .JSONtoNBT_Object(JsonHelper.ReadFromFile(chapterFile.toFile()), new NBTTagCompound(), true);
                UUID chapterUuid = NBTConverter.UuidValueType.QUEST_LINE.readId(tag);
                if (!importedChapterUuids.contains(chapterUuid)) {
                    continue;
                }
                if (containsFileBackedQuest(subdirectory, fileQuestUuids)) {
                    resolved.add(chapterUuid);
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse BetterQuesting chapter file {}", chapterFile, e);
            }
        }
        return resolved;
    }

    private boolean containsFileBackedQuest(File chapterDirectory, Set<UUID> fileQuestUuids) {
        File[] files = chapterDirectory.listFiles(
            file -> file.isFile() && file.getName()
                .endsWith(".json") && !QUEST_LINE_FILE.equals(file.getName()));
        if (files == null) {
            return false;
        }
        for (File file : files) {
            try {
                NBTTagCompound tag = NBTConverter
                    .JSONtoNBT_Object(JsonHelper.ReadFromFile(file), new NBTTagCompound(), true);
                UUID questUuid = NBTConverter.UuidValueType.QUEST.readId(tag);
                if (fileQuestUuids.contains(questUuid)) {
                    return true;
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to parse BetterQuesting chapter entry file {}", file, e);
            }
        }
        return false;
    }

    private void readLegacyOverrides(File legacyFile, BQDefinitionRegistry registry, Set<UUID> fileQuestUuids,
        Set<UUID> fileChapterUuids) {
        try {
            NBTTagCompound root = NBTConverter
                .JSONtoNBT_Object(JsonHelper.ReadFromFile(legacyFile), new NBTTagCompound(), true);
            Set<UUID> importedQuestUuids = registry.getImportedQuestUuids();
            NBTTagList questDatabase = root.getTagList("questDatabase", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < questDatabase.tagCount(); i++) {
                UUID questUuid = NBTConverter.UuidValueType.QUEST.readId(questDatabase.getCompoundTagAt(i));
                if (importedQuestUuids.contains(questUuid)) {
                    fileQuestUuids.add(questUuid);
                }
            }
            Set<UUID> importedChapterUuids = registry.getImportedChapterUuids();
            NBTTagList questLines = root.getTagList("questLines", Constants.NBT.TAG_COMPOUND);
            for (int i = 0; i < questLines.tagCount(); i++) {
                NBTTagCompound chapterTag = questLines.getCompoundTagAt(i);
                UUID chapterUuid = NBTConverter.UuidValueType.QUEST_LINE.readId(chapterTag);
                if (!importedChapterUuids.contains(chapterUuid)) {
                    continue;
                }
                NBTTagList questEntries = chapterTag.getTagList("quests", Constants.NBT.TAG_COMPOUND);
                for (int entryIndex = 0; entryIndex < questEntries.tagCount(); entryIndex++) {
                    UUID questUuid = NBTConverter.UuidValueType.QUEST.readId(questEntries.getCompoundTagAt(entryIndex));
                    if (fileQuestUuids.contains(questUuid)) {
                        fileChapterUuids.add(chapterUuid);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse BetterQuesting legacy default file {}", legacyFile, e);
        }
    }

    private void readQuestUuid(Path questFile, Set<UUID> importedQuestUuids, Set<UUID> resolved) {
        try {
            NBTTagCompound tag = NBTConverter
                .JSONtoNBT_Object(JsonHelper.ReadFromFile(questFile.toFile()), new NBTTagCompound(), true);
            UUID questUuid = NBTConverter.UuidValueType.QUEST.readId(tag);
            if (importedQuestUuids.contains(questUuid)) {
                resolved.add(questUuid);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to parse BetterQuesting quest file {}", questFile, e);
        }
    }
}
