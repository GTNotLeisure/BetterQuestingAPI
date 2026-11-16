package com.hfstudio.bqapi.runtime;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;

public class BQRegistrationInfo {

    private static final int MAX_STACK_DEPTH = 12;
    private static final String BQAPI_PKG = "com.hfstudio.bqapi";

    private final String registrantClass;
    private final String registrantMethod;
    private final int registrantLine;
    private final String modId;
    private final List<StackTraceElement> stack;
    private final long registrationTimeMs;

    private BQRegistrationInfo(String registrantClass, String registrantMethod, int registrantLine, String modId,
        List<StackTraceElement> stack, long registrationTimeMs) {
        this.registrantClass = registrantClass;
        this.registrantMethod = registrantMethod;
        this.registrantLine = registrantLine;
        this.modId = modId;
        this.stack = stack;
        this.registrationTimeMs = registrationTimeMs;
    }

    /**
     * Treats the first frame outside {@code com.hfstudio.bqapi}, {@code java.*}, and {@code sun.*}
     * as the registration source.
     */
    public static BQRegistrationInfo capture() {
        // FML's active mod is only reliable during mod initialization.
        String modId = "unknown";
        try {
            ModContainer active = Loader.instance()
                .activeModContainer();
            if (active != null) {
                modId = active.getModId();
            }
        } catch (Exception ignored) {}

        StackTraceElement[] full = Thread.currentThread()
            .getStackTrace();
        StackTraceElement callerFrame = null;
        int externalStart = 0;
        for (int i = 0; i < full.length; i++) {
            String cls = full[i].getClassName();
            if (!cls.startsWith(BQAPI_PKG) && !cls.startsWith("java.") && !cls.startsWith("sun.")) {
                callerFrame = full[i];
                externalStart = i;
                break;
            }
        }

        StackTraceElement[] relevant;
        if (externalStart > 0 && externalStart < full.length) {
            int len = Math.min(full.length - externalStart, MAX_STACK_DEPTH);
            relevant = Arrays.copyOfRange(full, externalStart, externalStart + len);
        } else {
            relevant = new StackTraceElement[0];
        }

        String cls = callerFrame != null ? callerFrame.getClassName() : "unknown";
        String method = callerFrame != null ? callerFrame.getMethodName() : "unknown";
        int line = callerFrame != null ? callerFrame.getLineNumber() : -1;

        return new BQRegistrationInfo(
            cls,
            method,
            line,
            modId,
            Collections.unmodifiableList(Arrays.asList(relevant)),
            System.currentTimeMillis());
    }

    /**
     * Fully-qualified class name of the first external call frame.
     */
    public String getRegistrantClass() {
        return registrantClass;
    }

    public String getRegistrantMethod() {
        return registrantMethod;
    }

    /**
     * Source line number of the first external call frame, or {@code -1} if unavailable.
     */
    public int getRegistrantLine() {
        return registrantLine;
    }

    /**
     * FML's active mod ID, or {@code "unknown"} when unavailable.
     */
    public String getModId() {
        return modId;
    }

    /**
     * Immutable stack from the registration source, capped at {@value #MAX_STACK_DEPTH} frames.
     */
    public List<StackTraceElement> getStack() {
        return stack;
    }

    /**
     * Registration time in milliseconds since the Unix epoch.
     */
    public long getRegistrationTimeMs() {
        return registrationTimeMs;
    }
}
