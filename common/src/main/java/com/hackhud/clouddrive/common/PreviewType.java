package com.hackhud.clouddrive.common;

/**
 * Типи файлів варіанту 9, вміст яких показується при виборі кліком:
 * .html (як текст, не розмітка) і .png.
 */
public enum PreviewType {
    TEXT,
    IMAGE,
    NONE;

    public static PreviewType of(String fileName) {
        return switch (FileListOps.extension(fileName)) {
            case "html" -> TEXT;
            case "png" -> IMAGE;
            default -> NONE;
        };
    }
}
