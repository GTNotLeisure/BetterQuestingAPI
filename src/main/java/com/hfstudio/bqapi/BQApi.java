package com.hfstudio.bqapi;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.UnaryOperator;

import net.minecraft.server.MinecraftServer;

import com.hfstudio.bqapi.api.QuestReloadService;
import com.hfstudio.bqapi.api.QuestReloader;
import com.hfstudio.bqapi.api.builder.Quests;
import com.hfstudio.bqapi.api.definition.ChapterDefinition;
import com.hfstudio.bqapi.api.definition.QuestDefinition;
import com.hfstudio.bqapi.api.importer.ImportedQuestFolder;
import com.hfstudio.bqapi.api.importer.ImportedQuestFolders;
import com.hfstudio.bqapi.runtime.BQDefinitionRegistry;
import com.hfstudio.bqapi.runtime.BQRegistrationInfo;
import com.hfstudio.bqapi.runtime.BQReloaderRegistry;
import com.hfstudio.bqapi.runtime.BQRuntime;
import com.hfstudio.bqapi.runtime.BQStableIdFactory;
import com.hfstudio.bqapi.runtime.importer.BQImportedQuestLoader;

public class BQApi {

    private static final BQDefinitionRegistry REGISTRY = new BQDefinitionRegistry();
    private static final BQReloaderRegistry RELOADER_REGISTRY = new BQReloaderRegistry();

    private static final BQRuntime RUNTIME = new BQRuntime(REGISTRY, RELOADER_REGISTRY);

    private BQApi() {}

    public static void register(ChapterDefinition chapter) {
        REGISTRY.register(chapter);
    }

    public static void registerAll(Collection<ChapterDefinition> chapters) {
        REGISTRY.registerAll(chapters);
    }

    public static List<ChapterDefinition> importFolder(String modId, String rootPath) {
        return importFolder(
            ImportedQuestFolders.folder(modId, rootPath)
                .build());
    }

    public static List<ChapterDefinition> importFolder(ImportedQuestFolder folder) {
        return new BQImportedQuestLoader().importFolder(folder);
    }

    public static void registerImportedFolder(String modId, String rootPath) {
        registerImportedFolder(
            ImportedQuestFolders.folder(modId, rootPath)
                .build());
    }

    public static void registerImportedFolder(ImportedQuestFolder folder) {
        registerAll(importFolder(folder));
    }

    public static Optional<ChapterDefinition> getChapter(String chapterId) {
        return REGISTRY.getChapter(chapterId);
    }

    public static Optional<ChapterDefinition> getChapter(UUID chapterUuid) {
        return REGISTRY.getChapter(chapterUuid);
    }

    public static boolean hasChapter(String chapterId) {
        return REGISTRY.hasChapter(chapterId);
    }

    public static boolean hasChapter(UUID chapterUuid) {
        return REGISTRY.hasChapter(chapterUuid);
    }

    public static Optional<QuestDefinition> getQuest(UUID questUuid) {
        return REGISTRY.getQuest(questUuid);
    }

    public static Optional<QuestDefinition> getQuest(String questId) {
        return REGISTRY.getQuest(questId);
    }

    public static boolean hasQuest(UUID questUuid) {
        return REGISTRY.hasQuest(questUuid);
    }

    public static boolean hasQuest(String questId) {
        return REGISTRY.hasQuest(questId);
    }

    public static Set<UUID> getRegisteredChapterUuids() {
        return REGISTRY.getRegisteredChapterUuids();
    }

    public static Set<UUID> getRegisteredQuestUuids() {
        return REGISTRY.getRegisteredQuestUuids();
    }

    public static Set<UUID> getManagedChapterUuids() {
        return REGISTRY.getManagedChapterUuids();
    }

    public static Set<UUID> getManagedQuestUuids() {
        return REGISTRY.getManagedQuestUuids();
    }

    public static Set<UUID> getSaveFilteredChapterUuids() {
        return REGISTRY.getSaveFilteredChapterUuids();
    }

    public static Set<UUID> getSaveFilteredQuestUuids() {
        return REGISTRY.getSaveFilteredQuestUuids();
    }

    /**
     * Returns an immutable snapshot, unaffected by later registrations.
     */
    public static Collection<ChapterDefinition> getRegisteredChapters() {
        return REGISTRY.getChapters();
    }

    /**
     * Metadata from the chapter's latest registration.
     */
    public static Optional<BQRegistrationInfo> getChapterRegistrationInfo(String chapterId) {
        return REGISTRY.getChapterRegistrationInfo(chapterId);
    }

    public static Optional<BQRegistrationInfo> getChapterRegistrationInfo(UUID chapterUuid) {
        return REGISTRY.getChapterRegistrationInfo(chapterUuid);
    }

    /**
     * Uses the containing chapter's registration metadata.
     */
    public static Optional<BQRegistrationInfo> getQuestRegistrationInfo(UUID questUuid) {
        return REGISTRY.getQuestChapterId(questUuid)
            .flatMap(REGISTRY::getChapterRegistrationInfo);
    }

    public static Optional<BQRegistrationInfo> getQuestRegistrationInfo(String questId) {
        return REGISTRY.getQuest(questId)
            .flatMap(quest -> getQuestRegistrationInfo(quest.getUuid()));
    }

    /**
     * Patch sources in registration order, as an immutable snapshot.
     */
    public static List<BQRegistrationInfo> getQuestPatchRegistrationInfos(UUID questUuid) {
        return REGISTRY.getPatchRegistrationInfos(questUuid);
    }

    public static int getPatchedQuestCount() {
        return REGISTRY.getPatchedQuestCount();
    }

    /**
     * Each reinjection starts from the registered definition and applies patches in registration order.
     * Each patch receives the previous result; the registered definition remains unchanged.
     *
     * <p>
     * Register once at startup. Patches persist across reloads, so registering them in a reload
     * callback adds another copy on every cycle.
     *
     * @param patchFn must return a non-null definition with the same ID and UUID
     * @throws IllegalArgumentException during application if a patch changes the quest ID or UUID
     * @see Quests#copyOf(QuestDefinition)
     */
    public static void patchQuest(UUID questUuid, UnaryOperator<QuestDefinition> patchFn) {
        REGISTRY.registerQuestPatch(questUuid, patchFn);
    }

    /**
     * Targets the UUID derived by {@link BQStableIdFactory#quest(String)}.
     * Use the UUID overload for quests registered with a custom UUID.
     *
     * @see #patchQuest(UUID, UnaryOperator)
     */
    public static void patchQuest(String questId, UnaryOperator<QuestDefinition> patchFn) {
        patchQuest(BQStableIdFactory.quest(questId), patchFn);
    }

    /**
     * Registers a public {@link QuestReloader} class declaring {@code public static void reloadQuest()}.
     * Repeated registration of the same class is ignored.
     *
     * @throws IllegalArgumentException if the class does not satisfy the contract
     */
    public static void registerReloader(Class<?> clazz) {
        RELOADER_REGISTRY.register(clazz);
    }

    /**
     * Registers a service without SPI discovery. Only the first instance of each implementation class is used.
     *
     * @see QuestReloadService
     */
    public static void registerReloaderService(QuestReloadService service) {
        RELOADER_REGISTRY.registerService(service);
    }

    public static BQRuntime getRuntime() {
        return RUNTIME;
    }

    /**
     * Applies registrations and synchronizes their scope on the owning server thread.
     */
    public static void reinject(MinecraftServer server) {
        RUNTIME.reinject(server);
    }
}
