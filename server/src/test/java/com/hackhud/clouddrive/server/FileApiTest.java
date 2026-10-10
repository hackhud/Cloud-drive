package com.hackhud.clouddrive.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:test;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class FileApiTest {

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("app.data-dir", () -> dataDir.toString());
    }

    /** Керований годинник: кожен виклик просуває час на 1 хвилину. */
    static class TickingClock extends Clock {
        private Instant now = Instant.parse("2026-10-20T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public synchronized Instant instant() {
            now = now.plus(Duration.ofMinutes(1));
            return now;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        Clock testClock() {
            return new TickingClock();
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    private String token;
    private String user;

    @BeforeEach
    void register() throws Exception {
        user = "u" + UUID.randomUUID().toString().substring(0, 8);
        String body = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"secret\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        token = json.readTree(body).get("token").asText();
    }

    private JsonNode upload(String name, String text) throws Exception {
        String body = mvc.perform(multipart("/api/files")
                        .file(new MockMultipartFile("file", name, "application/octet-stream", text.getBytes()))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    @Test
    void filesRequireAuthorization() throws Exception {
        mvc.perform(get("/api/files")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/files").header("Authorization", "Bearer wrong")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void uploadStoresAllAttributes() throws Exception {
        JsonNode file = upload("Main.kt", "fun main() {}");
        assertEquals("Main.kt", file.get("name").asText());
        assertEquals(13, file.get("size").asLong());
        assertEquals(user, file.get("uploadedBy").asText());
        assertEquals(user, file.get("editedBy").asText());
        assertEquals(file.get("createdAt").asText(), file.get("modifiedAt").asText());
    }

    @Test
    void reuploadReplacesContentAndUpdatesModified() throws Exception {
        JsonNode first = upload("notes.html", "<b>v1</b>");
        JsonNode second = upload("notes.html", "<b>version 2</b>");
        assertEquals(first.get("id").asLong(), second.get("id").asLong());
        assertEquals(first.get("createdAt").asText(), second.get("createdAt").asText());
        assertTrue(Instant.parse(second.get("modifiedAt").asText())
                .isAfter(Instant.parse(first.get("modifiedAt").asText())));
        mvc.perform(get("/api/files/" + second.get("id").asLong() + "/content")
                        .header("Authorization", "Bearer " + token))
                .andExpect(content().string("<b>version 2</b>"));
    }

    @Test
    void htmlIsServedAsPlainText() throws Exception {
        long id = upload("page.html", "<h1>Hi</h1>").get("id").asLong();
        mvc.perform(get("/api/files/" + id + "/content").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/plain;charset=UTF-8"))
                .andExpect(content().string("<h1>Hi</h1>"));
    }

    @Test
    void individualOperationSortsByModifiedAndFiltersKtJpg() throws Exception {
        upload("b.kt", "1");
        upload("a.jpg", "2");
        upload("c.html", "3");
        upload("d.kt", "4");
        upload("b.kt", "edited");

        mvc.perform(get("/api/files?order=desc&filter=kt_jpg").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].name").value("b.kt"))
                .andExpect(jsonPath("$[1].name").value("d.kt"))
                .andExpect(jsonPath("$[2].name").value("a.jpg"));

        mvc.perform(get("/api/files?order=asc").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].name").value("a.jpg"))
                .andExpect(jsonPath("$[3].name").value("b.kt"));
    }

    @Test
    void usersSeeOnlyTheirOwnFilesAndCanDelete() throws Exception {
        long id = upload("private.png", "x").get("id").asLong();
        String mine = token;
        register();
        mvc.perform(get("/api/files").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/files/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/files/" + id).header("Authorization", "Bearer " + mine))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/files").header("Authorization", "Bearer " + mine))
                .andExpect(jsonPath("$.length()").value(0));
    }
}
