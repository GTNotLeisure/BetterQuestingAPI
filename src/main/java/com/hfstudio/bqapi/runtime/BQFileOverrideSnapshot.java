package com.hfstudio.bqapi.runtime;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class BQFileOverrideSnapshot {

    private static final BQFileOverrideSnapshot EMPTY = new BQFileOverrideSnapshot(
        Collections.emptySet(),
        Collections.emptySet());

    private final Set<UUID> fileBackedQuestUuids;
    private final Set<UUID> fileBackedChapterUuids;

    public BQFileOverrideSnapshot(Collection<UUID> fileBackedQuestUuids, Collection<UUID> fileBackedChapterUuids) {
        this.fileBackedQuestUuids = Collections.unmodifiableSet(new LinkedHashSet<>(fileBackedQuestUuids));
        this.fileBackedChapterUuids = Collections.unmodifiableSet(new LinkedHashSet<>(fileBackedChapterUuids));
    }

    public static BQFileOverrideSnapshot empty() {
        return EMPTY;
    }

    public Set<UUID> getFileBackedQuestUuids() {
        return fileBackedQuestUuids;
    }

    public Set<UUID> getFileBackedChapterUuids() {
        return fileBackedChapterUuids;
    }
}
