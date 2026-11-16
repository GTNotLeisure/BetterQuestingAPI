package com.hfstudio.bqapi.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.hfstudio.bqapi.api.builder.Chapters;
import com.hfstudio.bqapi.api.builder.Quests;
import com.hfstudio.bqapi.api.builder.RewardBuilders;
import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;
import com.hfstudio.bqapi.runtime.apply.ChapterApplier;
import com.hfstudio.bqapi.runtime.apply.QuestApplier;
import com.hfstudio.bqapi.runtime.apply.TaskApplier;

public class BQApplyPipelineTest {

    @Test
    public void sharedQuestsArePatchedAndAppliedOnlyOnce() {
        BQDefinitionRegistry registry = new BQDefinitionRegistry();
        QuestDefinition quest = Quests.quest("test:shared")
            .build();
        registry.register(
            Chapters.chapter("test:first")
                .quest(quest.at(0, 0))
                .build());
        registry.register(
            Chapters.chapter("test:second")
                .quest(quest.at(1, 1))
                .build());
        AtomicInteger patchCalls = new AtomicInteger();
        registry.registerQuestPatch(quest.getUuid(), definition -> {
            patchCalls.incrementAndGet();
            return definition;
        });
        RecordingQuestApplier quests = new RecordingQuestApplier();
        RecordingChapterApplier chapters = new RecordingChapterApplier();

        BQApplyResult result = new BQRuntimeApplier(registry, quests, chapters).apply(null);

        assertEquals(1, patchCalls.get());
        assertEquals(1, quests.applied.size());
        assertEquals(2, chapters.applied.size());
        assertEquals(Set.of(quest.getUuid()), result.questUuids());
    }

    @Test
    public void invalidLaterQuestPreventsAllLiveWrites() {
        BQDefinitionRegistry registry = new BQDefinitionRegistry();
        QuestDefinition first = Quests.quest("test:valid")
            .build();
        QuestDefinition invalid = Quests.quest("test:invalid")
            .reward(
                RewardBuilders.item("test:first_reward")
                    .slot(0)
                    .build())
            .reward(
                RewardBuilders.item("test:duplicate_reward")
                    .slot(0)
                    .build())
            .build();
        registry.register(
            Chapters.chapter("test:chapter")
                .quest(first.at(0, 0))
                .quest(invalid.at(1, 1))
                .build());
        RecordingQuestApplier quests = new RecordingQuestApplier();
        RecordingChapterApplier chapters = new RecordingChapterApplier();
        BQRuntimeApplier applier = new BQRuntimeApplier(registry, quests, chapters);

        assertThrows(IllegalArgumentException.class, () -> applier.apply(null));
        assertTrue(quests.applied.isEmpty());
        assertTrue(chapters.applied.isEmpty());
    }

    private static class RecordingQuestApplier extends QuestApplier {

        private final List<UUID> applied = new ArrayList<>();

        private RecordingQuestApplier() {
            super(new TaskApplier());
        }

        @Override
        public void apply(PreparedQuest prepared) {
            applied.add(
                prepared.definition()
                    .getUuid());
        }
    }

    private static class RecordingChapterApplier extends ChapterApplier {

        private final List<UUID> applied = new ArrayList<>();

        @Override
        public void apply(ChapterDefinition definition, Set<UUID> managedQuestUuids) {
            applied.add(definition.getUuid());
        }
    }
}
