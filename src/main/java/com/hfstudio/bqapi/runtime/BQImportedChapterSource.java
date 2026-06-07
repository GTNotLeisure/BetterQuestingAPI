package com.hfstudio.bqapi.runtime;

import java.util.Objects;

import com.hfstudio.bqapi.api.importer.ImportedQuestFolder;

public class BQImportedChapterSource {

    private final ImportedQuestFolder folder;
    private final String lineDirectory;

    public BQImportedChapterSource(ImportedQuestFolder folder, String lineDirectory) {
        this.folder = Objects.requireNonNull(folder, "folder");
        this.lineDirectory = Objects.requireNonNull(lineDirectory, "lineDirectory");
    }

    public ImportedQuestFolder getFolder() {
        return folder;
    }

    public String getLineDirectory() {
        return lineDirectory;
    }
}
