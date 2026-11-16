package com.hfstudio.bqapi.runtime;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record BQApplyResult(Set<UUID> questUuids, Set<UUID> chapterUuids) {

    public BQApplyResult {
        questUuids = Collections.unmodifiableSet(new LinkedHashSet<>(questUuids));
        chapterUuids = Collections.unmodifiableSet(new LinkedHashSet<>(chapterUuids));
    }

    public boolean isEmpty() {
        return questUuids.isEmpty() && chapterUuids.isEmpty();
    }
}
