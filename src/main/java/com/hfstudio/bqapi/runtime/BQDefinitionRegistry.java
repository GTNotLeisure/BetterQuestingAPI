package com.hfstudio.bqapi.runtime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;

public final class BQDefinitionRegistry {

    private final Map<String, ChapterDefinition> chapters = new LinkedHashMap<>();
    private final Map<UUID, String> chapterIdsByUuid = new LinkedHashMap<>();
    private final Map<UUID, BQImportedChapterSource> importedChapterSources = new LinkedHashMap<>();

    // Quest index: rebuilt whenever a new chapter is registered.
    private final Map<UUID, QuestDefinition> questsByUuid = new LinkedHashMap<>();
    private final Map<String, QuestDefinition> questsById = new LinkedHashMap<>();

    // Quest → containing chapter: rebuilt together with the quest index.
    private final Map<UUID, String> questChapterIdByUuid = new LinkedHashMap<>();
    private final Map<UUID, UUID> importedQuestChapterByQuestUuid = new LinkedHashMap<>();

    // Runtime patch lists, applied in registration order during each reinject cycle.
    private final Map<UUID, List<UnaryOperator<QuestDefinition>>> questPatches = new LinkedHashMap<>();

    // Debug: registration metadata captured at the time each chapter was registered.
    private final Map<String, BQRegistrationInfo> chapterRegInfo = new LinkedHashMap<>();

    // Debug: registration metadata captured each time a quest patch was added.
    private final Map<UUID, List<BQRegistrationInfo>> questPatchRegInfo = new LinkedHashMap<>();
    private BQFileOverrideSnapshot fileOverrideSnapshot = BQFileOverrideSnapshot.empty();
    private Set<UUID> managedChapterUuids = Collections.emptySet();
    private Set<UUID> managedQuestUuids = Collections.emptySet();
    private Set<UUID> saveFilteredChapterUuids = Collections.emptySet();
    private Set<UUID> saveFilteredQuestUuids = Collections.emptySet();

    public synchronized void register(ChapterDefinition chapter) {
        BQImportedChapterSource importedSource = chapter.getImportedSource();
        ChapterDefinition value = Objects.requireNonNull(chapter, "chapter");
        String existingId = chapterIdsByUuid.get(value.getUuid());
        if (existingId != null && !existingId.equals(value.getId())) {
            chapters.remove(existingId);
            chapterRegInfo.remove(existingId);
            importedChapterSources.remove(value.getUuid());
        }
        ChapterDefinition previous = chapters.put(value.getId(), value);
        if (previous != null && !previous.getUuid()
            .equals(value.getUuid())) {
            chapterIdsByUuid.remove(previous.getUuid());
            importedChapterSources.remove(previous.getUuid());
        }
        chapterIdsByUuid.put(value.getUuid(), value.getId());
        if (importedSource != null) {
            importedChapterSources.put(value.getUuid(), importedSource);
        } else {
            importedChapterSources.remove(value.getUuid());
        }
        chapterRegInfo.put(value.getId(), BQRegistrationInfo.capture());
        rebuildQuestIndex();
        refreshDerivedViews();
    }

    /**
     * Registers a runtime patch for the quest identified by {@code questUuid}.
     * Patches are applied in registration order during each reinject cycle and
     * do not modify the original registered data. Multiple patches may be added
     * for the same quest.
     */
    public synchronized void registerQuestPatch(UUID questUuid, UnaryOperator<QuestDefinition> patch) {
        Objects.requireNonNull(questUuid, "questUuid");
        Objects.requireNonNull(patch, "patch");
        questPatches.computeIfAbsent(questUuid, k -> new ArrayList<>())
            .add(patch);
        questPatchRegInfo.computeIfAbsent(questUuid, k -> new ArrayList<>())
            .add(BQRegistrationInfo.capture());
    }

    /**
     * Applies all registered patches for the given quest in registration order and
     * returns the final result. Returns the quest unchanged if no patches are registered.
     */
    public synchronized QuestDefinition applyPatchesTo(QuestDefinition quest) {
        Objects.requireNonNull(quest, "quest");
        List<UnaryOperator<QuestDefinition>> patches = questPatches.get(quest.getUuid());
        if (patches == null || patches.isEmpty()) {
            return quest;
        }
        QuestDefinition result = quest;
        for (UnaryOperator<QuestDefinition> p : patches) {
            result = Objects.requireNonNull(p.apply(result), "quest patch must not return null");
        }
        return result;
    }

    public synchronized Optional<ChapterDefinition> getChapter(String id) {
        return Optional.ofNullable(chapters.get(id));
    }

    public synchronized Optional<ChapterDefinition> getChapter(UUID uuid) {
        String id = chapterIdsByUuid.get(uuid);
        return id == null ? Optional.empty() : Optional.ofNullable(chapters.get(id));
    }

    public synchronized boolean hasChapter(String id) {
        return chapters.containsKey(id);
    }

    public synchronized boolean hasChapter(UUID uuid) {
        return chapterIdsByUuid.containsKey(uuid);
    }

    public synchronized Optional<QuestDefinition> getQuest(UUID uuid) {
        return Optional.ofNullable(questsByUuid.get(uuid));
    }

    public synchronized Optional<QuestDefinition> getQuest(String id) {
        return Optional.ofNullable(questsById.get(id));
    }

    public synchronized boolean hasQuest(UUID uuid) {
        return questsByUuid.containsKey(uuid);
    }

    public synchronized boolean hasQuest(String id) {
        return questsById.containsKey(id);
    }

    public synchronized Collection<ChapterDefinition> getChapters() {
        return Collections.unmodifiableCollection(chapters.values());
    }

    public synchronized Set<UUID> getRegisteredChapterUuids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(chapterIdsByUuid.keySet()));
    }

    public synchronized Set<UUID> getRegisteredQuestUuids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(questsByUuid.keySet()));
    }

    public synchronized Set<UUID> getManagedChapterUuids() {
        return managedChapterUuids;
    }

    public synchronized Set<UUID> getManagedQuestUuids() {
        return managedQuestUuids;
    }

    public synchronized Set<UUID> getSaveFilteredChapterUuids() {
        return saveFilteredChapterUuids;
    }

    public synchronized Set<UUID> getSaveFilteredQuestUuids() {
        return saveFilteredQuestUuids;
    }

    public synchronized Set<UUID> getImportedChapterUuids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(importedChapterSources.keySet()));
    }

    public synchronized Set<UUID> getImportedQuestUuids() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(importedQuestChapterByQuestUuid.keySet()));
    }

    public synchronized boolean isImportedChapter(UUID chapterUuid) {
        return importedChapterSources.containsKey(chapterUuid);
    }

    public synchronized boolean isImportedQuest(UUID questUuid) {
        return importedQuestChapterByQuestUuid.containsKey(questUuid);
    }

    public synchronized void setFileOverrideSnapshot(BQFileOverrideSnapshot snapshot) {
        this.fileOverrideSnapshot = Objects.requireNonNull(snapshot, "snapshot");
        refreshDerivedViews();
    }

    /** Returns the registration info captured when the chapter with the given ID was registered. */
    public synchronized Optional<BQRegistrationInfo> getChapterRegistrationInfo(String chapterId) {
        return Optional.ofNullable(chapterRegInfo.get(chapterId));
    }

    /** Returns the registration info for the chapter identified by {@code uuid}. */
    public synchronized Optional<BQRegistrationInfo> getChapterRegistrationInfo(UUID chapterUuid) {
        String id = chapterIdsByUuid.get(chapterUuid);
        return id == null ? Optional.empty() : getChapterRegistrationInfo(id);
    }

    /**
     * Returns the ID of the chapter that contains the quest with the given UUID,
     * or {@link Optional#empty()} if the quest is not registered.
     */
    public synchronized Optional<String> getQuestChapterId(UUID questUuid) {
        return Optional.ofNullable(questChapterIdByUuid.get(questUuid));
    }

    /**
     * Returns all patch registration infos for the quest with the given UUID,
     * in registration order. Returns an empty list if no patches have been registered.
     */
    public synchronized List<BQRegistrationInfo> getPatchRegistrationInfos(UUID questUuid) {
        List<BQRegistrationInfo> list = questPatchRegInfo.get(questUuid);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }

    /** Returns the number of quests that have at least one runtime patch registered. */
    public synchronized int getPatchedQuestCount() {
        return questPatches.size();
    }

    private void rebuildQuestIndex() {
        questsByUuid.clear();
        questsById.clear();
        questChapterIdByUuid.clear();
        importedQuestChapterByQuestUuid.clear();
        for (ChapterDefinition chapter : chapters.values()) {
            for (QuestDefinition q : chapter.getAllQuests()) {
                questsByUuid.put(q.getUuid(), q);
                questsById.put(q.getId(), q);
                questChapterIdByUuid.put(q.getUuid(), chapter.getId());
                if (importedChapterSources.containsKey(chapter.getUuid())) {
                    importedQuestChapterByQuestUuid.put(q.getUuid(), chapter.getUuid());
                }
            }
        }
    }

    private void refreshDerivedViews() {
        Set<UUID> newManagedChapters = new LinkedHashSet<>(chapterIdsByUuid.keySet());
        for (UUID chapterUuid : fileOverrideSnapshot.getFileBackedChapterUuids()) {
            if (importedChapterSources.containsKey(chapterUuid)) {
                newManagedChapters.remove(chapterUuid);
            }
        }

        Set<UUID> newManagedQuests = new LinkedHashSet<>(questsByUuid.keySet());
        for (UUID questUuid : fileOverrideSnapshot.getFileBackedQuestUuids()) {
            if (importedQuestChapterByQuestUuid.containsKey(questUuid)) {
                newManagedQuests.remove(questUuid);
            }
        }

        Set<UUID> newSaveFilteredChapters = new LinkedHashSet<>(chapterIdsByUuid.keySet());
        newSaveFilteredChapters.removeAll(importedChapterSources.keySet());

        Set<UUID> newSaveFilteredQuests = new LinkedHashSet<>(questsByUuid.keySet());
        newSaveFilteredQuests.removeAll(importedQuestChapterByQuestUuid.keySet());

        managedChapterUuids = Collections.unmodifiableSet(newManagedChapters);
        managedQuestUuids = Collections.unmodifiableSet(newManagedQuests);
        saveFilteredChapterUuids = Collections.unmodifiableSet(newSaveFilteredChapters);
        saveFilteredQuestUuids = Collections.unmodifiableSet(newSaveFilteredQuests);
    }
}
