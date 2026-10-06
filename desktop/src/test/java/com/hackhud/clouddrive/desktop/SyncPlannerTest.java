package com.hackhud.clouddrive.desktop;

import org.junit.jupiter.api.Test;
import com.hackhud.clouddrive.common.FileInfo;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncPlannerTest {

    private static final Instant T = Instant.parse("2026-10-20T12:00:00Z");

    private static FileInfo remote(String name, Instant modified) {
        return new FileInfo(1, name, 1, T, modified, "artem", "artem");
    }

    @Test
    void uploadsNewAndNewerLocalFilesAndDownloadsTheRest() {
        Map<String, Instant> local = Map.of(
                "new.kt", T,
                "changed.html", T.plusSeconds(600),
                "same.png", T,
                "old.jpg", T);
        List<FileInfo> remote = List.of(
                remote("changed.html", T),
                remote("same.png", T.plusMillis(500)),
                remote("old.jpg", T.plusSeconds(600)),
                remote("only-remote.txt", T));

        List<SyncPlanner.Action> plan = SyncPlanner.plan(local, remote);

        assertEquals(4, plan.size());
        assertTrue(plan.contains(new SyncPlanner.Action(SyncPlanner.Direction.UPLOAD, "new.kt", null)));
        assertTrue(plan.stream().anyMatch(a -> a.name().equals("changed.html")
                && a.direction() == SyncPlanner.Direction.UPLOAD));
        assertTrue(plan.stream().anyMatch(a -> a.name().equals("old.jpg")
                && a.direction() == SyncPlanner.Direction.DOWNLOAD));
        assertTrue(plan.stream().anyMatch(a -> a.name().equals("only-remote.txt")
                && a.direction() == SyncPlanner.Direction.DOWNLOAD));
    }

    @Test
    void nothingToDoWhenInSync() {
        assertTrue(SyncPlanner.plan(Map.of("a.png", T), List.of(remote("a.png", T))).isEmpty());
    }
}
