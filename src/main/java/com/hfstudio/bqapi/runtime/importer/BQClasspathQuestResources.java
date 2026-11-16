package com.hfstudio.bqapi.runtime.importer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import net.minecraft.nbt.NBTTagCompound;

import com.google.gson.JsonParser;
import com.hfstudio.bqapi.api.importer.ImportedQuestFolder;

import betterquesting.api.utils.NBTConverter;
import betterquesting.api.utils.UuidConverter;

public class BQClasspathQuestResources {

    private final ImportedQuestFolder folder;
    private final Map<String, NBTTagCompound> nbtCache = new HashMap<>();
    private final Map<String, Map<String, Boolean>> directoryCache = new HashMap<>();
    private Map<UUID, Integer> orderIndex;
    private boolean jarIndexed;

    public BQClasspathQuestResources(ImportedQuestFolder folder) {
        this.folder = Objects.requireNonNull(folder, "folder");
    }

    public NBTTagCompound readNbt(String relativePath) throws IOException {
        String path = ImportedQuestFolder.normalizeRelativePath(relativePath);
        NBTTagCompound cached = nbtCache.get(path);
        if (cached == null) {
            try (InputStream input = open(path)) {
                if (input == null) {
                    throw new IOException("Missing resource " + resolve(path));
                }
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                    cached = NBTConverter.JSONtoNBT_Object(
                        new JsonParser().parse(reader)
                            .getAsJsonObject(),
                        new NBTTagCompound(),
                        true);
                }
            } catch (RuntimeException e) {
                throw new IOException("Invalid quest resource " + resolve(path), e);
            }
            nbtCache.put(path, cached);
        }
        return (NBTTagCompound) cached.copy();
    }

    public Map<UUID, Integer> readQuestLineOrder() throws IOException {
        if (orderIndex != null) {
            return orderIndex;
        }
        Map<UUID, Integer> order = new LinkedHashMap<>();
        try (InputStream input = open("QuestLinesOrder.txt")) {
            if (input == null) {
                throw new IOException("Missing resource " + resolve("QuestLinesOrder.txt"));
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String value = line.trim();
                    if (value.isEmpty()) {
                        continue;
                    }
                    int separator = value.indexOf(':');
                    String encoded = separator < 0 ? value
                        : value.substring(0, separator)
                            .trim();
                    order.putIfAbsent(UuidConverter.decodeUuid(encoded), order.size());
                }
            }
        }
        orderIndex = Collections.unmodifiableMap(order);
        return orderIndex;
    }

    public List<String> listDirectories(String relativeDir) throws IOException {
        List<String> directories = new ArrayList<>();
        listImmediateChildren(relativeDir).forEach((name, directory) -> {
            if (directory) {
                directories.add(name);
            }
        });
        Collections.sort(directories);
        return directories;
    }

    public List<String> listFiles(String relativeDir, String suffix) throws IOException {
        List<String> files = new ArrayList<>();
        listImmediateChildren(relativeDir).forEach((name, directory) -> {
            if (!directory && name.endsWith(suffix)) {
                files.add(name);
            }
        });
        Collections.sort(files);
        return files;
    }

    protected Map<String, Boolean> listImmediateChildren(String relativeDir) throws IOException {
        String path = ImportedQuestFolder.normalizeRelativePath(relativeDir);
        Map<String, Boolean> cached = directoryCache.get(path);
        if (cached != null) {
            return cached;
        }
        URL marker = folder.getClassLoader()
            .getResource(resolve("QuestLinesOrder.txt"));
        if (marker == null) {
            throw new IOException("Missing marker resource " + resolve("QuestLinesOrder.txt"));
        }
        if ("file".equals(marker.getProtocol())) {
            try {
                File root = new File(marker.toURI()).getParentFile();
                File[] files = new File(root, path).listFiles();
                Map<String, Boolean> children = new LinkedHashMap<>();
                if (files != null) {
                    for (File file : files) {
                        children.put(file.getName(), file.isDirectory());
                    }
                }
                cached = Collections.unmodifiableMap(children);
                directoryCache.put(path, cached);
                return cached;
            } catch (URISyntaxException e) {
                throw new IOException("Invalid resource directory " + marker, e);
            }
        }
        if (!jarIndexed) {
            indexJar(marker);
        }
        return directoryCache.getOrDefault(path, Collections.emptyMap());
    }

    private void indexJar(URL marker) throws IOException {
        URLConnection rawConnection = marker.openConnection();
        if (!(rawConnection instanceof JarURLConnection connection)) {
            throw new IOException("Unsupported quest resource protocol: " + marker.getProtocol());
        }
        // The importer must never close the classloader's shared cached JarFile.
        connection.setUseCaches(false);
        String prefix = folder.getClasspathRoot() + "/";
        Map<String, Map<String, Boolean>> directories = new HashMap<>();
        try (JarFile jar = connection.getJarFile()) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (!name.startsWith(prefix) || name.equals(prefix)) {
                    continue;
                }
                String relative = name.substring(prefix.length());
                int start = 0;
                String directory = "";
                while (start < relative.length()) {
                    int slash = relative.indexOf('/', start);
                    boolean childDirectory = slash >= 0 || entry.isDirectory();
                    String child = slash < 0 ? relative.substring(start) : relative.substring(start, slash);
                    if (!child.isEmpty()) {
                        directories.computeIfAbsent(directory, key -> new LinkedHashMap<>())
                            .merge(child, childDirectory, Boolean::logicalOr);
                    }
                    if (slash < 0) {
                        break;
                    }
                    directory = relative.substring(0, slash);
                    start = slash + 1;
                }
            }
        }
        directories
            .forEach((directory, children) -> directoryCache.put(directory, Collections.unmodifiableMap(children)));
        jarIndexed = true;
    }

    protected InputStream open(String relativePath) throws IOException {
        return folder.getClassLoader()
            .getResourceAsStream(resolve(relativePath));
    }

    protected String resolve(String relativePath) {
        return folder.getClasspathRoot() + "/" + ImportedQuestFolder.normalizeRelativePath(relativePath);
    }
}
