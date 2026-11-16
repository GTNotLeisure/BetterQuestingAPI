package com.hfstudio.bqapi.runtime;

import java.util.Objects;

import net.minecraft.server.MinecraftServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import betterquesting.handlers.SaveLoadHandler;
import cpw.mods.fml.common.FMLCommonHandler;

public class BQRuntime {

    private static final Logger LOGGER = LogManager.getLogger(BQRuntime.class);

    private final BQDefinitionRegistry registry;
    private final BQReloaderRegistry reloaders;
    private final BQFileBackedOverrideResolver overrideResolver;
    private final BQRuntimeApplier applier;
    private final BQSyncService synchronizer;
    private MinecraftServer activeServer;
    private Thread serverThread;
    private boolean loadingWorld;
    private boolean reinjecting;
    private boolean servicesDiscovered;

    public BQRuntime(BQDefinitionRegistry registry, BQReloaderRegistry reloaders) {
        this(
            registry,
            reloaders,
            new BQFileBackedOverrideResolver(),
            new BQRuntimeApplier(registry),
            new BQSyncService());
    }

    public BQRuntime(BQDefinitionRegistry registry, BQReloaderRegistry reloaders,
        BQFileBackedOverrideResolver overrideResolver, BQRuntimeApplier applier, BQSyncService synchronizer) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.reloaders = Objects.requireNonNull(reloaders, "reloaders");
        this.overrideResolver = Objects.requireNonNull(overrideResolver, "overrideResolver");
        this.applier = Objects.requireNonNull(applier, "applier");
        this.synchronizer = Objects.requireNonNull(synchronizer, "synchronizer");
    }

    /**
     * Called by the owning server thread before BetterQuesting loads a world.
     */
    public synchronized void beginWorldLoad(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (reinjecting) {
            throw new IllegalStateException("Cannot load a world during quest reinjection");
        }
        activeServer = server;
        serverThread = Thread.currentThread();
        loadingWorld = true;
        registry.setFileOverrideSnapshot(BQFileOverrideSnapshot.empty());
        BQManagedQuestProgress.reset();
    }

    /**
     * Materializes definitions before either legacy or per-player progress is read.
     */
    public void beforeWorldProgress(MinecraftServer server) {
        synchronized (this) {
            requireServerThread(server);
            loadingWorld = false;
        }
        reinject(server, false);
    }

    /**
     * Completes an explicit database reload before BetterQuesting sends its own sync.
     */
    public void afterDatabaseLoad(MinecraftServer server) {
        reinject(server, false);
    }

    public void reinject(MinecraftServer server) {
        reinject(server, true);
    }

    public BQSyncService getSynchronizer() {
        return synchronizer;
    }

    public synchronized void unload() {
        if (reinjecting) {
            throw new IllegalStateException("Cannot unload quest state during reinjection");
        }
        activeServer = null;
        serverThread = null;
        loadingWorld = false;
        registry.setFileOverrideSnapshot(BQFileOverrideSnapshot.empty());
        BQManagedQuestProgress.reset();
    }

    private void reinject(MinecraftServer server, boolean synchronize) {
        if (server == null || !beginReinjection(server)) {
            return;
        }
        try {
            if (!servicesDiscovered) {
                reloaders.registerFromServiceLoader(BQRuntime.class.getClassLoader());
                servicesDiscovered = true;
            }
            reloaders.invokeAll();
            registry.setFileOverrideSnapshot(overrideResolver.resolve(registry));
            BQApplyResult result = applier.apply(server);
            BQManagedQuestProgress.replay(result.questUuids());
            if (!result.isEmpty()) {
                SaveLoadHandler.INSTANCE.markDirty();
                if (synchronize) {
                    synchronizer.syncManaged(server, result);
                }
            }
            LOGGER.debug(
                "Applied {} managed quests and {} chapters",
                result.questUuids()
                    .size(),
                result.chapterUuids()
                    .size());
        } finally {
            synchronized (this) {
                reinjecting = false;
            }
        }
    }

    private synchronized boolean beginReinjection(MinecraftServer server) {
        requireServerThread(server);
        if (loadingWorld || reinjecting) {
            return false;
        }
        reinjecting = true;
        return true;
    }

    private void requireServerThread(MinecraftServer server) {
        if (activeServer == null) {
            if (!FMLCommonHandler.instance()
                .getEffectiveSide()
                .isServer()) {
                throw new IllegalStateException("Quest reinjection must run on the server thread");
            }
            activeServer = server;
            serverThread = Thread.currentThread();
        }
        if (activeServer != server || serverThread != Thread.currentThread()) {
            throw new IllegalStateException("Quest reinjection must use the active server and its owning thread");
        }
    }
}
