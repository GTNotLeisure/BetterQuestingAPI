package com.hfstudio.bqapi.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.hfstudio.bqapi.BQApi;

/**
 * Compatibility alternative to {@link QuestReloadService} for static reload callbacks.
 *
 * <p>
 * Register the public annotated class once with {@link BQApi#registerReloader(Class)}.
 * It must declare {@code public static void reloadQuest()}. The callback runs before definitions
 * are applied, including after {@code /bq_admin default load}, so its registrations take effect
 * in the same cycle.
 *
 * <p>
 * Callbacks must be idempotent. Register persistent quest patches at startup, outside the callback.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface QuestReloader {}
