package com.hackhud.clouddrive.desktop;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hackhud.clouddrive.common.FileInfo;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP-клієнт до REST API сервера.
 */
public class ApiClient {

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final String baseUrl;
    private String token;
    private String username;

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
    }

    public String username() {
        return username;
    }

    public void login(String user, String password) throws IOException {
        authenticate("/api/auth/login", user, password);
    }

    public void register(String user, String password) throws IOException {
        authenticate("/api/auth/register", user, password);
    }

    private void authenticate(String path, String user, String password) throws IOException {
        String body = json.writeValueAsString(Map.of("username", user, "password", password));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        JsonNode session = json.readTree(send(request, HttpResponse.BodyHandlers.ofString()));
        token = session.get("token").asText();
        username = session.get("username").asText();
    }

    public void logout() throws IOException {
        send(authorized("/api/auth/logout").POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.discarding());
        token = null;
    }

    public List<FileInfo> list() throws IOException {
        String body = send(authorized("/api/files").GET().build(), HttpResponse.BodyHandlers.ofString());
        return Arrays.asList(json.readValue(body, FileInfo[].class));
    }

    public FileInfo upload(Path file) throws IOException {
        String boundary = "----cloud-drive-" + UUID.randomUUID();
        String name = file.getFileName().toString().replace("\"", "");
        byte[] head = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + name + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        HttpRequest request = authorized("/api/files")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.concat(
                        HttpRequest.BodyPublishers.ofByteArray(head),
                        HttpRequest.BodyPublishers.ofFile(file),
                        HttpRequest.BodyPublishers.ofByteArray(tail)))
                .build();
        return json.readValue(send(request, HttpResponse.BodyHandlers.ofString()), FileInfo.class);
    }

    public byte[] content(long id) throws IOException {
        return send(authorized("/api/files/" + id + "/content").GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
    }

    public void download(FileInfo file, Path target) throws IOException {
        Path tmp = Files.createTempFile(target.toAbsolutePath().getParent(), "download", ".tmp");
        send(authorized("/api/files/" + file.id() + "/content?download=true").GET().build(),
                HttpResponse.BodyHandlers.ofFile(tmp));
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public void delete(long id) throws IOException {
        send(authorized("/api/files/" + id).DELETE().build(), HttpResponse.BodyHandlers.discarding());
    }

    private HttpRequest.Builder authorized(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Authorization", "Bearer " + token);
    }

    private <T> T send(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            HttpResponse<T> response = http.send(request, handler);
            if (response.statusCode() >= 400) {
                throw new IOException(errorMessage(response));
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Запит перервано", e);
        }
    }

    private String errorMessage(HttpResponse<?> response) {
        if (response.statusCode() == 401) {
            return "Потрібна авторизація (невірний логін/пароль або сесія завершилась)";
        }
        if (response.body() instanceof String body && !body.isBlank()) {
            try {
                JsonNode node = json.readTree(body);
                if (node.hasNonNull("message") && !node.get("message").asText().isBlank()) {
                    return node.get("message").asText();
                }
            } catch (IOException ignored) {
                // відповідь не JSON
            }
        }
        return "Помилка сервера: HTTP " + response.statusCode();
    }
}
