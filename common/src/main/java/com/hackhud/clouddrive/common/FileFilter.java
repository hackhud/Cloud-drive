package com.hackhud.clouddrive.common;

import java.util.Set;

/**
 * Фільтр списку файлів (варіант 8): усі файли або лише файли типу .kt, .jpg.
 */
public enum FileFilter {
    ALL("Усі файли", Set.of()),
    KT_JPG(".kt, .jpg", Set.of("kt", "jpg"));

    private final String title;
    private final Set<String> extensions;

    FileFilter(String title, Set<String> extensions) {
        this.title = title;
        this.extensions = extensions;
    }

    public boolean accepts(String fileName) {
        return extensions.isEmpty() || extensions.contains(FileListOps.extension(fileName));
    }

    public static FileFilter parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        return valueOf(value.trim().toUpperCase());
    }

    @Override
    public String toString() {
        return title;
    }
}
