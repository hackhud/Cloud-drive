package com.hackhud.clouddrive.common;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Індивідуальна операція варіанту 8: сортування за датою і часом модифікації
 * (зростання і спадання) та фільтр «усі файли / лише .kt, .jpg».
 */
public final class FileListOps {

    private FileListOps() {
    }

    public static List<FileInfo> sortByModified(List<FileInfo> files, SortOrder order) {
        if (order == SortOrder.NONE) {
            return List.copyOf(files);
        }
        Comparator<FileInfo> comparator = Comparator.comparing(FileInfo::modifiedAt)
                .thenComparing(FileInfo::name, String.CASE_INSENSITIVE_ORDER);
        if (order == SortOrder.DESC) {
            comparator = comparator.reversed();
        }
        return files.stream().sorted(comparator).toList();
    }

    public static List<FileInfo> filter(List<FileInfo> files, FileFilter filter) {
        return files.stream().filter(f -> filter.accepts(f.name())).toList();
    }

    public static List<FileInfo> apply(List<FileInfo> files, FileFilter filter, SortOrder order) {
        return sortByModified(filter(files, filter), order);
    }

    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
