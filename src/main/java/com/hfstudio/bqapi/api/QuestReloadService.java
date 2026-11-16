package com.hfstudio.bqapi.api;

import java.util.ServiceLoader;

import com.hfstudio.bqapi.BQApi;

/**
 * Reload callback discovered through Java {@link ServiceLoader}.
 *
 * <p>
 * List implementation class names, one per line, in
 * {@code META-INF/services/com.hfstudio.bqapi.api.QuestReloadService}.
 * Only the first instance of each implementation class is registered.
 *
 * <p>
 * Callbacks run before definitions are applied and must be idempotent.
 * Register persistent patches through {@link BQApi#patchQuest} at startup, outside the callback.
 */
public interface QuestReloadService {

    void reloadQuest();
}
