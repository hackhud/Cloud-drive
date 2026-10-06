package com.hackhud.clouddrive.desktop;

import com.hackhud.clouddrive.common.FileInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Визначає, які файли треба завантажити на сервер, а які скачати, щоб локальна папка
 * і віддалений диск містили однакові останні версії файлів.
 * Порівняння за назвою і часом зміни; видалення не синхронізуються.
 */
public final class SyncPlanner {

    /** Допуск на різницю годинників і точність часу файлової системи. */
    static final Duration TOLERANCE = Duration.ofSeconds(2);

    public enum Direction { UPLOAD, DOWNLOAD }

    public record Action(Direction direction, String name, FileInfo remote) {
    }

    private SyncPlanner() {
    }

    public static List<Action> plan(Map<String, Instant> local, List<FileInfo> remote) {
        Map<String, FileInfo> remoteByName = remote.stream()
                .collect(Collectors.toMap(FileInfo::name, Function.identity()));
        List<Action> actions = new ArrayList<>();
        local.forEach((name, localModified) -> {
            FileInfo r = remoteByName.get(name);
            if (r == null || localModified.isAfter(r.modifiedAt().plus(TOLERANCE))) {
                actions.add(new Action(Direction.UPLOAD, name, r));
            } else if (r.modifiedAt().isAfter(localModified.plus(TOLERANCE))) {
                actions.add(new Action(Direction.DOWNLOAD, name, r));
            }
        });
        remote.stream()
                .filter(r -> !local.containsKey(r.name()))
                .forEach(r -> actions.add(new Action(Direction.DOWNLOAD, r.name(), r)));
        return actions;
    }
}
