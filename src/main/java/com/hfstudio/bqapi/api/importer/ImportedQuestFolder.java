package com.hfstudio.bqapi.api.importer;

import java.util.Objects;

public class ImportedQuestFolder {

    private final String modId;
    private final String rootPath;
    private final ClassLoader classLoader;

    public ImportedQuestFolder(String modId, String rootPath, ClassLoader classLoader) {
        this.modId = normalizeModId(modId);
        this.rootPath = normalizeRootPath(rootPath);
        this.classLoader = classLoader == null ? ImportedQuestFolder.class.getClassLoader() : classLoader;
    }

    public String getModId() {
        return modId;
    }

    public String getRootPath() {
        return rootPath;
    }

    public ClassLoader getClassLoader() {
        return classLoader;
    }

    public String getClasspathRoot() {
        return "assets/" + modId + "/" + rootPath;
    }

    private static String normalizeModId(String modId) {
        String value = Objects.requireNonNull(modId, "modId")
            .trim();
        if (!value.matches("[A-Za-z0-9_.-]+") || value.equals(".") || value.equals("..")) {
            throw new IllegalArgumentException("modId must be a resource namespace: " + value);
        }
        return value;
    }

    private static String normalizeRootPath(String rootPath) {
        return normalizeRelativePath(rootPath);
    }

    /**
     * Normalizes a resource path while rejecting traversal outside its declared root.
     */
    public static String normalizeRelativePath(String path) {
        String value = Objects.requireNonNull(path, "path")
            .replace('\\', '/')
            .trim();
        while (value.startsWith("/")) {
            value = value.substring(1);
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty()) {
            throw new IllegalArgumentException("resource path must not be empty");
        }
        String[] segments = value.split("/+", -1);
        for (String segment : segments) {
            if (segment.equals(".") || segment.equals("..")
                || segment.indexOf(':') >= 0
                || segment.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Invalid resource path: " + path);
            }
        }
        return String.join("/", segments);
    }

    public static class Builder {

        private final String modId;
        private final String rootPath;
        private ClassLoader classLoader;

        public Builder(String modId, String rootPath) {
            this.modId = modId;
            this.rootPath = rootPath;
        }

        public Builder classLoader(ClassLoader classLoader) {
            this.classLoader = classLoader;
            return this;
        }

        public ImportedQuestFolder build() {
            return new ImportedQuestFolder(modId, rootPath, classLoader);
        }
    }
}
