package com.hackhud.clouddrive.common;

import java.time.Instant;

/**
 * Атрибути файлу у віртуальному диску користувача.
 */
public record FileInfo(
        long id,
        String name,
        long size,
        Instant createdAt,
        Instant modifiedAt,
        String uploadedBy,
        String editedBy) {

    public String extension() {
        return FileListOps.extension(name);
    }
}
