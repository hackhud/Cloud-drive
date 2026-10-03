package com.hackhud.clouddrive.common;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FileListOpsTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

    private static FileInfo file(long id, String name, long minutes) {
        Instant modified = T0.plusSeconds(minutes * 60);
        return new FileInfo(id, name, 10, T0, modified, "artem", "artem");
    }

    private final List<FileInfo> files = List.of(
            file(1, "Main.kt", 30),
            file(2, "photo.jpg", 10),
            file(3, "index.html", 20),
            file(4, "logo.png", 40),
            file(5, "Notes.KT", 5));

    private static List<String> names(List<FileInfo> list) {
        return list.stream().map(FileInfo::name).toList();
    }

    @Test
    void sortsByModifiedAscending() {
        List<FileInfo> sorted = FileListOps.sortByModified(files, SortOrder.ASC);
        assertEquals(List.of("Notes.KT", "photo.jpg", "index.html", "Main.kt", "logo.png"), names(sorted));
    }

    @Test
    void sortsByModifiedDescending() {
        List<FileInfo> sorted = FileListOps.sortByModified(files, SortOrder.DESC);
        assertEquals(List.of("logo.png", "Main.kt", "index.html", "photo.jpg", "Notes.KT"), names(sorted));
    }

    @Test
    void filterKeepsOnlyKtAndJpgIgnoringCase() {
        List<FileInfo> filtered = FileListOps.filter(files, FileFilter.KT_JPG);
        assertEquals(List.of("Main.kt", "photo.jpg", "Notes.KT"), names(filtered));
    }

    @Test
    void filterAllKeepsEverything() {
        assertEquals(files.size(), FileListOps.filter(files, FileFilter.ALL).size());
    }

    @Test
    void filterAndSortCombined() {
        List<FileInfo> result = FileListOps.apply(files, FileFilter.KT_JPG, SortOrder.DESC);
        assertEquals(List.of("Main.kt", "photo.jpg", "Notes.KT"), names(result));
    }

    @Test
    void previewTypeMatchesVariant() {
        assertEquals(PreviewType.TEXT, PreviewType.of("page.HTML"));
        assertEquals(PreviewType.IMAGE, PreviewType.of("logo.png"));
        assertEquals(PreviewType.NONE, PreviewType.of("photo.jpg"));
    }
}
