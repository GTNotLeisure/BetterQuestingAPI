package com.hfstudio.bqapi.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;

public record BQDefinitionSnapshot(List<ChapterDefinition> chapters, List<QuestDefinition> quests,
    Map<UUID, List<UnaryOperator<QuestDefinition>>> questPatches, Set<UUID> managedChapterUuids,
    Set<UUID> managedQuestUuids) {

    public BQDefinitionSnapshot {
        chapters = List.copyOf(chapters);
        quests = List.copyOf(quests);
        Map<UUID, List<UnaryOperator<QuestDefinition>>> patches = new LinkedHashMap<>();
        questPatches.forEach((uuid, callbacks) -> patches.put(uuid, List.copyOf(callbacks)));
        questPatches = Collections.unmodifiableMap(patches);
        managedChapterUuids = Collections.unmodifiableSet(new LinkedHashSet<>(managedChapterUuids));
        managedQuestUuids = Collections.unmodifiableSet(new LinkedHashSet<>(managedQuestUuids));
    }

    public QuestDefinition applyPatchesTo(QuestDefinition quest) {
        return applyPatches(quest, questPatches.getOrDefault(quest.getUuid(), Collections.emptyList()));
    }

    public static QuestDefinition applyPatches(QuestDefinition quest, List<UnaryOperator<QuestDefinition>> patches) {
        Objects.requireNonNull(quest, "quest");
        QuestDefinition result = quest;
        for (UnaryOperator<QuestDefinition> patch : new ArrayList<>(patches)) {
            result = Objects.requireNonNull(patch.apply(result), "quest patch must not return null");
            if (!quest.getUuid()
                .equals(result.getUuid())
                || !quest.getId()
                    .equals(result.getId())) {
                throw new IllegalArgumentException("Quest patches must preserve ID and UUID: " + quest.getId());
            }
        }
        return result;
    }
}
