package com.hfstudio.bqapi.runtime.importer;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;

import com.hfstudio.bqapi.api.builder.QuestBuilder;
import com.hfstudio.bqapi.api.builder.Quests;
import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;
import com.hfstudio.bqapi.api.definition.QuestPlacementDefinition;
import com.hfstudio.bqapi.api.definition.RawRewardDefinition;
import com.hfstudio.bqapi.api.definition.RawTaskDefinition;
import com.hfstudio.bqapi.api.importer.ImportedQuestFolder;
import com.hfstudio.bqapi.runtime.BQImportedChapterSource;
import com.hfstudio.bqapi.runtime.BQStableIdFactory;

import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.UuidConverter;

public class BQImportedQuestLoader {

    public List<ChapterDefinition> importFolder(ImportedQuestFolder folder) {
        Objects.requireNonNull(folder, "folder");
        try {
            BQClasspathQuestResources resources = createResources(folder);
            Map<UUID, ImportedQuestFileRef> questFiles = indexQuestFiles(resources);
            Map<UUID, Integer> orderIndex = resources.readQuestLineOrder();
            List<ImportedLineData> lines = new ArrayList<>();
            for (String lineDirectory : resources.listDirectories("QuestLines")) {
                lines.add(readLine(resources, folder, lineDirectory, questFiles));
            }

            lines.sort(Comparator.comparingInt(line -> orderIndex.getOrDefault(line.uuid, Integer.MAX_VALUE)));

            List<ChapterDefinition> chapters = new ArrayList<>(lines.size());
            UUID previous = null;
            for (ImportedLineData line : lines) {
                chapters
                    .add(toChapterDefinition(defaultChapterId(folder, line.lineDirectory), line, line.uuid, previous));
                previous = line.uuid;
            }
            return chapters;
        } catch (IOException e) {
            throw new IllegalArgumentException(
                "Failed to import BetterQuesting folder " + folder.getClasspathRoot(),
                e);
        }
    }

    public ChapterDefinition importLine(String chapterId, ImportedQuestFolder folder, String lineDirectory,
        UUID explicitUuid, boolean uuidFromResource, String explicitOrderAfterEncoded, NBTTagCompound explicitIconNbt) {
        Objects.requireNonNull(chapterId, "chapterId");
        Objects.requireNonNull(folder, "folder");
        Objects.requireNonNull(lineDirectory, "lineDirectory");
        try {
            lineDirectory = ImportedQuestFolder.normalizeRelativePath(lineDirectory);
            BQClasspathQuestResources resources = createResources(folder);
            Map<UUID, ImportedQuestFileRef> questFiles = indexQuestFiles(resources);
            ImportedLineData line = readLine(resources, folder, lineDirectory, questFiles);
            UUID previous = resolvePreviousLineUuid(resources.readQuestLineOrder(), resources, folder, lineDirectory);
            UUID chapterUuid = explicitUuid != null ? explicitUuid
                : uuidFromResource ? line.uuid : BQStableIdFactory.chapter(chapterId);
            String orderAfterEncoded = explicitOrderAfterEncoded != null ? explicitOrderAfterEncoded
                : previous == null ? null : UuidConverter.encodeUuid(previous);
            return toChapterDefinition(
                chapterId,
                line,
                chapterUuid,
                orderAfterEncoded,
                explicitIconNbt == null ? line.iconNbt : explicitIconNbt);
        } catch (IOException e) {
            throw new IllegalArgumentException(
                "Failed to import BetterQuesting line " + lineDirectory + " from " + folder.getClasspathRoot(),
                e);
        }
    }

    private ChapterDefinition toChapterDefinition(String chapterId, ImportedLineData line, UUID chapterUuid,
        UUID previousUuid) {
        return toChapterDefinition(
            chapterId,
            line,
            chapterUuid,
            previousUuid == null ? null : UuidConverter.encodeUuid(previousUuid),
            line.iconNbt);
    }

    private ChapterDefinition toChapterDefinition(String chapterId, ImportedLineData line, UUID chapterUuid,
        String orderAfterEncoded, NBTTagCompound iconNbt) {
        return new ChapterDefinition(
            chapterId,
            chapterUuid,
            orderAfterEncoded,
            iconNbt,
            line.templateNbt,
            line.placements,
            line.extraQuests,
            new BQImportedChapterSource(line.folder, line.lineDirectory));
    }

    private ImportedLineData readLine(BQClasspathQuestResources resources, ImportedQuestFolder folder,
        String lineDirectory, Map<UUID, ImportedQuestFileRef> questFiles) throws IOException {
        NBTTagCompound lineNbt = resources.readNbt("QuestLines/" + lineDirectory + "/QuestLine.json");

        UUID lineUuid = NBTConverter.UuidValueType.QUEST_LINE.readId((NBTTagCompound) lineNbt.copy());
        Map<UUID, QuestDefinition> questsByUuid = new LinkedHashMap<>();
        for (String questFile : resources.listFiles("Quests/" + lineDirectory, ".json")) {
            QuestDefinition quest = readQuest(resources, folder, lineDirectory, questFile);
            questsByUuid.put(quest.getUuid(), quest);
        }
        List<QuestPlacementDefinition> placements = new ArrayList<>();
        Set<UUID> placedQuestIds = new HashSet<>();
        NBTTagList questPlacements = lineNbt.getTagList("quests", 10);
        if (questPlacements.tagCount() > 0) {
            // Older exports embed placements in QuestLine.json.
            for (int i = 0; i < questPlacements.tagCount(); i++) {
                NBTTagCompound placementNbt = questPlacements.getCompoundTagAt(i);
                UUID questUuid = NBTConverter.UuidValueType.QUEST.readId((NBTTagCompound) placementNbt.copy());
                QuestDefinition quest = resolvePlacedQuest(resources, folder, questFiles, questsByUuid, questUuid);
                int sizeX = placementNbt.hasKey("size", 99) ? placementNbt.getInteger("size")
                    : placementNbt.getInteger("sizeX");
                int sizeY = placementNbt.hasKey("size", 99) ? placementNbt.getInteger("size")
                    : placementNbt.getInteger("sizeY");
                placedQuestIds.add(questUuid);
                placements.add(quest.at(placementNbt.getInteger("x"), placementNbt.getInteger("y"), sizeX, sizeY));
            }
        } else {
            // Native exports store each placement alongside QuestLine.json.
            for (String placementFile : resources.listFiles("QuestLines/" + lineDirectory, ".json")) {
                if (placementFile.equals("QuestLine.json")) {
                    continue;
                }
                NBTTagCompound placementNbt = resources.readNbt("QuestLines/" + lineDirectory + "/" + placementFile);
                UUID questUuid = NBTConverter.UuidValueType.QUEST.readId((NBTTagCompound) placementNbt.copy());
                QuestDefinition quest = resolvePlacedQuest(resources, folder, questFiles, questsByUuid, questUuid);
                int sizeX = placementNbt.hasKey("size", 99) ? placementNbt.getInteger("size")
                    : placementNbt.getInteger("sizeX");
                int sizeY = placementNbt.hasKey("size", 99) ? placementNbt.getInteger("size")
                    : placementNbt.getInteger("sizeY");
                placedQuestIds.add(questUuid);
                placements.add(quest.at(placementNbt.getInteger("x"), placementNbt.getInteger("y"), sizeX, sizeY));
            }
        }

        resolveReferencedQuests(resources, folder, questFiles, questsByUuid);
        List<QuestDefinition> extraQuests = new ArrayList<>();
        for (Map.Entry<UUID, QuestDefinition> questEntry : questsByUuid.entrySet()) {
            if (!placedQuestIds.contains(questEntry.getKey())) {
                extraQuests.add(questEntry.getValue());
            }
        }

        return new ImportedLineData(
            folder,
            lineDirectory,
            lineUuid,
            lineNbt,
            readItemProperty(lineNbt.getCompoundTag("properties"), "icon"),
            Collections.unmodifiableList(placements),
            Collections.unmodifiableList(extraQuests));
    }

    private QuestDefinition readQuest(BQClasspathQuestResources resources, ImportedQuestFolder folder,
        String lineDirectory, String questFile) throws IOException {
        NBTTagCompound questNbt = resources.readNbt("Quests/" + lineDirectory + "/" + questFile);
        UUID questUuid = NBTConverter.UuidValueType.QUEST.readId((NBTTagCompound) questNbt.copy());
        NBTTagCompound propertiesNbt = questNbt.getCompoundTag("properties");

        String questId = defaultQuestId(folder, lineDirectory, questUuid);
        QuestBuilder builder = Quests.quest(questId)
            .uuid(questUuid)
            .templateNbt(questNbt)
            .main(readBooleanProperty(propertiesNbt, "isMain", false));

        NBTTagCompound iconNbt = readItemProperty(propertiesNbt, "icon");
        if (iconNbt != null) {
            builder.iconNbt(iconNbt);
        }

        NBTTagList prerequisites = questNbt.getTagList("preRequisites", 10);
        for (int i = 0; i < prerequisites.tagCount(); i++) {
            NBTTagCompound prerequisiteNbt = prerequisites.getCompoundTagAt(i);
            UUID prerequisite = NBTConverter.UuidValueType.QUEST.readId((NBTTagCompound) prerequisiteNbt.copy());
            IQuest.RequirementType type = prerequisiteNbt.hasKey("type", 99)
                ? IQuest.RequirementType.from(prerequisiteNbt.getByte("type"))
                : IQuest.RequirementType.NORMAL;
            builder.prerequisite(prerequisite, type);
        }

        NBTTagList tasks = questNbt.getTagList("tasks", 10);
        for (int i = 0; i < tasks.tagCount(); i++) {
            NBTTagCompound rawTaskNbt = tasks.getCompoundTagAt(i);
            builder.task(
                new RawTaskDefinition(
                    questId + "/task/" + i,
                    rawTaskNbt.hasKey("index", 99) ? rawTaskNbt.getInteger("index") : -1,
                    new ResourceLocation(rawTaskNbt.getString("taskID")),
                    stripKeys(rawTaskNbt, "taskID", "index")));
        }

        NBTTagList rewards = questNbt.getTagList("rewards", 10);
        for (int i = 0; i < rewards.tagCount(); i++) {
            NBTTagCompound rawRewardNbt = rewards.getCompoundTagAt(i);
            builder.reward(
                new RawRewardDefinition(
                    questId + "/reward/" + i,
                    rawRewardNbt.hasKey("index", 99) ? rawRewardNbt.getInteger("index") : -1,
                    new ResourceLocation(rawRewardNbt.getString("rewardID")),
                    stripKeys(rawRewardNbt, "rewardID", "index")));
        }

        return builder.build();
    }

    protected BQClasspathQuestResources createResources(ImportedQuestFolder folder) {
        return new BQClasspathQuestResources(folder);
    }

    private QuestDefinition resolvePlacedQuest(BQClasspathQuestResources resources, ImportedQuestFolder folder,
        Map<UUID, ImportedQuestFileRef> questFiles, Map<UUID, QuestDefinition> quests, UUID questUuid)
        throws IOException {
        QuestDefinition quest = quests.get(questUuid);
        if (quest != null) {
            return quest;
        }
        ImportedQuestFileRef source = questFiles.get(questUuid);
        if (source == null) {
            throw new IllegalArgumentException(
                "Missing quest file for placement " + questUuid + " in " + folder.getClasspathRoot());
        }
        quest = readQuest(resources, folder, source.lineDirectory, source.questFile);
        quests.put(questUuid, quest);
        return quest;
    }

    private boolean readBooleanProperty(NBTTagCompound propertiesNbt, String key, boolean defaultValue) {
        if (propertiesNbt == null) {
            return defaultValue;
        }
        NBTTagCompound domain = propertiesNbt.getCompoundTag("betterquesting");
        if (!domain.hasKey(key, 99)) {
            return defaultValue;
        }
        return domain.getBoolean(key);
    }

    private NBTTagCompound readItemProperty(NBTTagCompound propertiesNbt, String key) {
        if (propertiesNbt == null) {
            return null;
        }
        NBTTagCompound domain = propertiesNbt.getCompoundTag("betterquesting");
        if (!domain.hasKey(key, 10)) {
            return null;
        }
        return (NBTTagCompound) domain.getCompoundTag(key)
            .copy();
    }

    private Map<UUID, ImportedQuestFileRef> indexQuestFiles(BQClasspathQuestResources resources) throws IOException {
        Map<UUID, ImportedQuestFileRef> questFiles = new LinkedHashMap<>();
        for (String questDirectory : resources.listDirectories("Quests")) {
            for (String questFile : resources.listFiles("Quests/" + questDirectory, ".json")) {
                NBTTagCompound questNbt = resources.readNbt("Quests/" + questDirectory + "/" + questFile);
                UUID questUuid = NBTConverter.UuidValueType.QUEST.readId((NBTTagCompound) questNbt.copy());
                ImportedQuestFileRef previous = questFiles
                    .putIfAbsent(questUuid, new ImportedQuestFileRef(questDirectory, questFile));
                if (previous != null) {
                    throw new IllegalArgumentException(
                        "Duplicate quest UUID " + questUuid
                            + " in "
                            + previous.lineDirectory
                            + "/"
                            + previous.questFile
                            + " and "
                            + questDirectory
                            + "/"
                            + questFile);
                }
            }
        }
        return questFiles;
    }

    private void resolveReferencedQuests(BQClasspathQuestResources resources, ImportedQuestFolder folder,
        Map<UUID, ImportedQuestFileRef> questFiles, Map<UUID, QuestDefinition> questsByUuid) throws IOException {
        Deque<UUID> pending = new ArrayDeque<>();
        Set<UUID> visited = new HashSet<>();
        for (QuestDefinition quest : questsByUuid.values()) {
            pending.addAll(quest.getPrerequisites());
        }

        while (!pending.isEmpty()) {
            UUID prerequisiteUuid = pending.removeFirst();
            if (!visited.add(prerequisiteUuid) || questsByUuid.containsKey(prerequisiteUuid)) {
                continue;
            }

            ImportedQuestFileRef questRef = questFiles.get(prerequisiteUuid);
            if (questRef == null) {
                continue;
            }

            QuestDefinition prerequisite = readQuest(resources, folder, questRef.lineDirectory, questRef.questFile);
            questsByUuid.put(prerequisite.getUuid(), prerequisite);
            pending.addAll(prerequisite.getPrerequisites());
        }
    }

    private UUID resolvePreviousLineUuid(Map<UUID, Integer> orderIndex, BQClasspathQuestResources resources,
        ImportedQuestFolder folder, String lineDirectory) throws IOException {
        List<ImportedLineRef> refs = new ArrayList<>();
        for (String dir : resources.listDirectories("QuestLines")) {
            NBTTagCompound lineNbt = resources.readNbt("QuestLines/" + dir + "/QuestLine.json");
            UUID lineUuid = NBTConverter.UuidValueType.QUEST_LINE.readId((NBTTagCompound) lineNbt.copy());
            refs.add(new ImportedLineRef(dir, lineUuid));
        }
        refs.sort(Comparator.comparingInt(ref -> orderIndex.getOrDefault(ref.uuid, Integer.MAX_VALUE)));
        UUID previous = null;
        for (ImportedLineRef ref : refs) {
            if (ref.lineDirectory.equals(lineDirectory)) {
                return previous;
            }
            previous = ref.uuid;
        }
        throw new IllegalArgumentException(
            "Unable to resolve imported line directory " + lineDirectory + " in " + folder.getClasspathRoot());
    }

    private NBTTagCompound stripKeys(NBTTagCompound source, String... keys) {
        NBTTagCompound copy = (NBTTagCompound) source.copy();
        for (String key : keys) {
            copy.removeTag(key);
        }
        return copy;
    }

    private String defaultChapterId(ImportedQuestFolder folder, String lineDirectory) {
        return folder.getModId() + ":imported/" + sanitize(folder.getRootPath()) + "/" + sanitize(lineDirectory);
    }

    private String defaultQuestId(ImportedQuestFolder folder, String lineDirectory, UUID questUuid) {
        return defaultChapterId(folder, lineDirectory) + "/quest/"
            + UuidConverter.encodeUuidStripPadding(questUuid)
                .replace('=', '_');
    }

    private String sanitize(String value) {
        return value.replace('\\', '/')
            .replaceAll("[^a-zA-Z0-9/_\\-.]", "_");
    }

    private record ImportedLineData(ImportedQuestFolder folder, String lineDirectory, UUID uuid,
        NBTTagCompound templateNbt, NBTTagCompound iconNbt, List<QuestPlacementDefinition> placements,
        List<QuestDefinition> extraQuests) {

        private ImportedLineData {
            templateNbt = (NBTTagCompound) templateNbt.copy();
            iconNbt = iconNbt == null ? null : (NBTTagCompound) iconNbt.copy();
        }
    }

    private record ImportedLineRef(String lineDirectory, UUID uuid) {}

    private record ImportedQuestFileRef(String lineDirectory, String questFile) {}
}
