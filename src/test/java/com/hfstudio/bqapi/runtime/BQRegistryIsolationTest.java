package com.hfstudio.bqapi.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.Test;

import com.hfstudio.bqapi.api.builder.Chapters;
import com.hfstudio.bqapi.api.builder.Quests;
import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;
import com.hfstudio.bqapi.api.importer.ImportedQuestFolders;

public class BQRegistryIsolationTest {

    @Test
    public void chapterQueryIsStableAcrossLaterRegistrations() {
        BQDefinitionRegistry registry = new BQDefinitionRegistry();
        registry.register(
            Chapters.chapter("test:first")
                .build());
        Collection<ChapterDefinition> snapshot = registry.getChapters();

        registry.register(
            Chapters.chapter("test:second")
                .build());

        assertEquals(1, snapshot.size());
        assertEquals(
            2,
            registry.getChapters()
                .size());
    }

    @Test
    public void patchMetadataQueryIsStableAcrossLaterRegistrations() {
        BQDefinitionRegistry registry = new BQDefinitionRegistry();
        UUID uuid = BQStableIdFactory.quest("test:quest");
        registry.registerQuestPatch(uuid, quest -> quest);
        List<BQRegistrationInfo> snapshot = registry.getPatchRegistrationInfos(uuid);

        registry.registerQuestPatch(uuid, quest -> quest);

        assertEquals(1, snapshot.size());
    }

    @Test
    public void patchCannotRetargetAnUnmanagedQuest() {
        BQDefinitionRegistry registry = new BQDefinitionRegistry();
        QuestDefinition original = Quests.quest("test:owned")
            .build();
        UUID foreignUuid = BQStableIdFactory.quest("other:foreign");
        registry.registerQuestPatch(
            original.getUuid(),
            quest -> Quests.copyOf(quest)
                .uuid(foreignUuid)
                .build());

        assertThrows(IllegalArgumentException.class, () -> registry.applyPatchesTo(original));
    }

    @Test
    public void patchesRegisteredDuringApplicationStartOnTheNextCycle() {
        BQDefinitionRegistry registry = new BQDefinitionRegistry();
        QuestDefinition original = Quests.quest("test:owned")
            .build();
        registry.registerQuestPatch(original.getUuid(), quest -> {
            registry.registerQuestPatch(
                quest.getUuid(),
                later -> Quests.copyOf(later)
                    .main(true)
                    .build());
            return quest;
        });

        assertEquals(original, registry.applyPatchesTo(original));
        assertTrue(
            registry.applyPatchesTo(original)
                .isMain());
    }

    @Test
    public void codeRegistrationReplacesImportedQuestOwnership() {
        BQDefinitionRegistry registry = new BQDefinitionRegistry();
        QuestDefinition quest = Quests.quest("test:shared")
            .build();
        BQImportedChapterSource source = new BQImportedChapterSource(
            ImportedQuestFolders.folder("testmod", "quests")
                .build(),
            "chapter");
        registry.register(
            new ChapterDefinition(
                "test:imported",
                UUID.randomUUID(),
                null,
                null,
                null,
                Collections.singletonList(quest.at(0, 0)),
                Collections.emptyList(),
                source));
        registry.register(
            Chapters.chapter("test:code")
                .quest(quest.at(0, 0))
                .build());

        assertFalse(registry.isImportedQuest(quest.getUuid()));
        assertTrue(
            registry.getSaveFilteredQuestUuids()
                .contains(quest.getUuid()));
    }
}
