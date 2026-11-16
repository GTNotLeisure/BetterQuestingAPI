package com.hfstudio.bqapi.api.builder;

import com.hfstudio.bqapi.api.definition.QuestDefinition;

public class Quests {

    private Quests() {}

    public static QuestBuilder quest(String id) {
        return QuestBuilder.quest(id);
    }

    public static QuestBuilder copyOf(QuestDefinition definition) {
        return QuestBuilder.from(definition);
    }
}
