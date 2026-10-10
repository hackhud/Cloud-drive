package com.hackhud.clouddrive.server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.hackhud.clouddrive.common.FileFilter;
import com.hackhud.clouddrive.common.FileInfo;
import com.hackhud.clouddrive.common.FileListOps;
import com.hackhud.clouddrive.common.SortOrder;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class FileService {

    private final FileRepository files;
    private final Path storageRoot;
    private final Clock clock;

    public FileService(FileRepository files, @Value("${app.data-dir}") Path dataDir, Clock clock) {
        this.files = files;
        this.storageRoot = dataDir.resolve("files");
        this.clock = clock;
    }

    public List<FileInfo> list(String owner, FileFilter filter, SortOrder order) {
        List<FileInfo> all = files.findByOwnerOrderByNameAsc(owner).stream().map(StoredFile::toInfo).toList();
        return FileListOps.apply(all, filter, order);
    }

    /**
     * Завантажує файл. Якщо файл з такою назвою вже є, його вміст замінюється
     * (це вважається редагуванням: оновлюються дата зміни і «хто редагував»).
     */
    @Transactional
    public FileInfo upload(String user, String fileName, InputStream content) {
        String name = cleanName(fileName);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Path tmp = writeTemp(content);
        long size = sizeOf(tmp);
        StoredFile file = files.findByOwnerAndName(user, name)
                .map(existing -> {
                    existing.replaced(size, user, now);
                    return existing;
                })
                .orElseGet(() -> new StoredFile(user, name, size, user, now));
        file = files.save(file);
        move(tmp, pathOf(file));
        return file.toInfo();
    }

    public StoredFile get(String owner, long id) {
        return files.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Файл не знайдено"));
    }

    public Path contentPath(StoredFile file) {
        return pathOf(file);
    }

    @Transactional
    public void delete(String owner, long id) {
        StoredFile file = get(owner, id);
        files.delete(file);
        try {
            Files.deleteIfExists(pathOf(file));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path pathOf(StoredFile file) {
        return storageRoot.resolve(file.getOwner()).resolve(String.valueOf(file.getId()));
    }

    private static String cleanName(String fileName) {
        String name = fileName == null ? "" : Path.of(fileName.replace('\\', '/')).getFileName().toString().trim();
        if (name.isEmpty() || name.equals(".") || name.equals("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некоректна назва файлу");
        }
        return name;
    }

    private Path writeTemp(InputStream content) {
        try {
            Files.createDirectories(storageRoot);
            Path tmp = Files.createTempFile(storageRoot, "upload", ".tmp");
            Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
            return tmp;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void move(Path from, Path to) {
        try {
            Files.createDirectories(to.getParent());
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
