package com.hackhud.clouddrive.server;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.hackhud.clouddrive.common.FileFilter;
import com.hackhud.clouddrive.common.FileInfo;
import com.hackhud.clouddrive.common.PreviewType;
import com.hackhud.clouddrive.common.SortOrder;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileController {

    private final FileService service;

    public FileController(FileService service) {
        this.service = service;
    }

    @GetMapping
    public List<FileInfo> list(@RequestAttribute(AuthInterceptor.USER) String user,
                               @RequestParam(defaultValue = "all") String filter,
                               @RequestParam(defaultValue = "none") String order) {
        return service.list(user, FileFilter.parse(filter), SortOrder.parse(order));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileInfo upload(@RequestAttribute(AuthInterceptor.USER) String user,
                           @RequestParam("file") MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream()) {
            return service.upload(user, file.getOriginalFilename(), in);
        }
    }

    /**
     * Вміст файлу. Файли .html віддаються як text/plain, щоб браузер показував їх як текст,
     * а не розмітку.
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<Resource> content(@RequestAttribute(AuthInterceptor.USER) String user,
                                            @PathVariable long id,
                                            @RequestParam(defaultValue = "false") boolean download) {
        StoredFile file = service.get(user, id);
        MediaType type = switch (PreviewType.of(file.getName())) {
            case TEXT -> new MediaType(MediaType.TEXT_PLAIN, StandardCharsets.UTF_8);
            case IMAGE -> MediaType.IMAGE_PNG;
            case NONE -> MediaType.APPLICATION_OCTET_STREAM;
        };
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(file.getName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(service.contentPath(file)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestAttribute(AuthInterceptor.USER) String user, @PathVariable long id) {
        service.delete(user, id);
        return ResponseEntity.noContent().build();
    }
}
