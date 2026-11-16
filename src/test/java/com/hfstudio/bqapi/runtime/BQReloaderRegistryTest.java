package com.hfstudio.bqapi.runtime;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.hfstudio.bqapi.api.QuestReloadService;

public class BQReloaderRegistryTest {

    @Test
    public void servicesRegisteredDuringReloadStartOnTheNextCycle() {
        BQReloaderRegistry registry = new BQReloaderRegistry();
        AtomicInteger calls = new AtomicInteger();
        QuestReloadService later = calls::incrementAndGet;
        registry.registerService(() -> registry.registerService(later));

        registry.invokeAll();
        assertEquals(0, calls.get());
        registry.invokeAll();
        assertEquals(1, calls.get());
    }
}
