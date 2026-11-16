package com.hfstudio.bqapi.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.ServiceLoader;
import java.util.Set;

import com.hfstudio.bqapi.api.QuestReloadService;
import com.hfstudio.bqapi.api.QuestReloader;

public class BQReloaderRegistry {

    private static final String RELOAD_METHOD = "reloadQuest";

    private final List<Method> reloaders = new ArrayList<>();

    private final List<QuestReloadService> reloadServices = new ArrayList<>();

    private final Set<Class<?>> reloadServiceClasses = new HashSet<>();

    /**
     * Requires a public {@link QuestReloader} class declaring {@code public static void reloadQuest()}.
     * Repeated registration of the same class is ignored.
     *
     * @throws IllegalArgumentException if the class fails validation
     */
    public synchronized void register(Class<?> clazz) {
        Objects.requireNonNull(clazz, "clazz");
        if (!clazz.isAnnotationPresent(QuestReloader.class)) {
            throw new IllegalArgumentException(clazz.getName() + " must be annotated with @QuestReloader");
        }

        Method method;
        try {
            method = clazz.getDeclaredMethod(RELOAD_METHOD);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(clazz.getName() + " must declare: public static void reloadQuest()", e);
        }

        if (!Modifier.isPublic(clazz.getModifiers()) || !Modifier.isPublic(method.getModifiers())
            || !Modifier.isStatic(method.getModifiers())
            || method.getReturnType() != void.class) {
            throw new IllegalArgumentException(clazz.getName() + ".reloadQuest() must be public static void");
        }

        for (Method existing : reloaders) {
            if (existing.getDeclaringClass() == clazz) {
                return;
            }
        }

        reloaders.add(method);
    }

    /**
     * Only the first instance of each implementation class is registered.
     */
    public synchronized void registerService(QuestReloadService service) {
        QuestReloadService value = Objects.requireNonNull(service, "service");
        if (!reloadServiceClasses.add(value.getClass())) {
            return;
        }
        reloadServices.add(value);
    }

    public void registerFromServiceLoader(ClassLoader classLoader) {
        ServiceLoader<QuestReloadService> loader = ServiceLoader.load(QuestReloadService.class, classLoader);
        for (QuestReloadService service : loader) {
            registerService(service);
        }
    }

    /**
     * Invokes a snapshot outside the registry lock, running SPI services before annotated static callbacks.
     * Registration order is preserved within each group.
     * Registrations made by callbacks take effect on the next invocation.
     *
     * @throws RuntimeException wrapping any exception thrown by a reloader
     */
    public void invokeAll() {
        List<QuestReloadService> services;
        List<Method> methods;
        synchronized (this) {
            services = new ArrayList<>(reloadServices);
            methods = new ArrayList<>(reloaders);
        }
        for (QuestReloadService service : services) {
            try {
                service.reloadQuest();
            } catch (RuntimeException e) {
                throw new RuntimeException(
                    "QuestReloadService threw an exception in " + service.getClass()
                        .getName(),
                    e);
            }
        }

        for (Method method : methods) {
            try {
                method.invoke(null);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                throw new RuntimeException(
                    "reloadQuest() threw an exception in " + method.getDeclaringClass()
                        .getName(),
                    cause);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(
                    "Cannot invoke reloadQuest() on " + method.getDeclaringClass()
                        .getName(),
                    e);
            }
        }
    }
}
