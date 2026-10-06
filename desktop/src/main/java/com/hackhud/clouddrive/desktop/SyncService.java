package com.hackhud.clouddrive.desktop;

import com.hackhud.clouddrive.common.FileInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Синхронізація обраної локальної папки (лише файли верхнього рівня) з віддаленим диском.
 */
public class SyncService {

    public record Result(int uploaded, int downloaded) {
    }

    private final ApiClient api;

    public SyncService(ApiClient api) {
        this.api = api;
    }

    public Result sync(Path folder) throws IOException {
        List<SyncPlanner.Action> actions = SyncPlanner.plan(localFiles(folder), api.list());
        int up = 0;
        int down = 0;
        for (SyncPlanner.Action action : actions) {
            Path local = folder.resolve(action.name());
            if (action.direction() == SyncPlanner.Direction.UPLOAD) {
                FileInfo uploaded = api.upload(local);
                // вирівнюємо час, щоб наступна синхронізація не вважала файл зміненим
                Files.setLastModifiedTime(local, FileTime.from(uploaded.modifiedAt()));
                up++;
            } else {
                api.download(action.remote(), local);
                Files.setLastModifiedTime(local, FileTime.from(action.remote().modifiedAt()));
                down++;
            }
        }
        return new Result(up, down);
    }

    private static Map<String, Instant> localFiles(Path folder) throws IOException {
        Map<String, Instant> result = new HashMap<>();
        try (Stream<Path> stream = Files.list(folder)) {
            for (Path p : stream.filter(Files::isRegularFile).toList()) {
                String name = p.getFileName().toString();
                if (!name.startsWith(".")) {
                    result.put(name, Files.getLastModifiedTime(p).toInstant());
                }
            }
        }
        return result;
    }
}
