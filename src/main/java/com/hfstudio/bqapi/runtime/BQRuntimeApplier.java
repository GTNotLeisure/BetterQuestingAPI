package com.hfstudio.bqapi.runtime;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;
import com.hfstudio.bqapi.runtime.apply.ChapterApplier;
import com.hfstudio.bqapi.runtime.apply.QuestApplier;
import com.hfstudio.bqapi.runtime.apply.QuestApplier.PreparedQuest;
import com.hfstudio.bqapi.runtime.apply.TaskApplier;

public class BQRuntimeApplier {

    private final BQDefinitionRegistry registry;
    private final QuestApplier questApplier;
    private final ChapterApplier chapterApplier;

    public BQRuntimeApplier(BQDefinitionRegistry registry) {
        this(registry, new QuestApplier(new TaskApplier()), new ChapterApplier());
    }

    public BQRuntimeApplier(BQDefinitionRegistry registry, QuestApplier questApplier, ChapterApplier chapterApplier) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.questApplier = Objects.requireNonNull(questApplier, "questApplier");
        this.chapterApplier = Objects.requireNonNull(chapterApplier, "chapterApplier");
    }

    public void applyAll(MinecraftServer server) {
        apply(server);
    }

    public BQApplyResult apply(MinecraftServer server) {
        BQDefinitionSnapshot snapshot = registry.snapshot();
        List<PreparedQuest> quests = new ArrayList<>();
        Set<UUID> appliedQuests = new LinkedHashSet<>();
        Set<UUID> appliedChapters = new LinkedHashSet<>();
        // Prepare every quest before writing so a preparation failure leaves live quests unchanged.
        for (QuestDefinition original : snapshot.quests()) {
            if (!snapshot.managedQuestUuids()
                .contains(original.getUuid())) {
                continue;
            }
            QuestDefinition patched = snapshot.applyPatchesTo(original);
            quests.add(questApplier.prepare(patched, patched != original));
        }
        for (PreparedQuest quest : quests) {
            questApplier.apply(quest);
            appliedQuests.add(
                quest.definition()
                    .getUuid());
        }
        for (ChapterDefinition chapter : snapshot.chapters()) {
            if (snapshot.managedChapterUuids()
                .contains(chapter.getUuid())) {
                chapterApplier.apply(chapter, snapshot.managedQuestUuids());
                appliedChapters.add(chapter.getUuid());
            }
        }
        return new BQApplyResult(appliedQuests, appliedChapters);
    }
}
