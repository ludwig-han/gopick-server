package com.gopick.server.project.service;

import com.gopick.server.project.dto.RecruitPostDraftRequest;
import com.gopick.server.project.exception.AiServiceException;
import com.gopick.server.project.exception.ProjectValidationException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class UpstageChatClientTests {
    private final JsonMapper mapper = new JsonMapper();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final CountDownLatch unblockProvider = new CountDownLatch(1);
    private HttpServer provider;
    private volatile int responseStatus;
    private volatile String responseBody;
    private volatile boolean stall;

    @BeforeEach
    void startProvider() throws IOException {
        responseStatus = 200;
        responseBody = completion("generated text", "stop");
        provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        provider.createContext("/v1/chat/completions", exchange -> {
            try (exchange) {
                requestBody.set(new String(exchange.getRequestBody().readAllBytes(), UTF_8));
                if (stall) {
                    unblockProvider.await(5, TimeUnit.SECONDS);
                }
                byte[] bytes = responseBody.getBytes(UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(responseStatus, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        provider.start();
    }

    @AfterEach
    void stopProvider() {
        unblockProvider.countDown();
        provider.stop(0);
    }

    @Test
    void submitsJsonWithTheConfiguredOutputLimit() {
        assertThat(complete(client("test-key", Duration.ofSeconds(2)), "project")).isEqualTo("generated text");

        var payload = mapper.readTree(requestBody.get());
        assertThat(payload.path("max_tokens").asInt()).isEqualTo(128);
        assertThat(payload.path("messages").get(1).path("content").asString()).isEqualTo("project");
    }

    @Test
    void doesNotCallTheProviderWhenTheInputExceedsTheUtf8Limit() {
        assertThatExceptionOfType(ProjectValidationException.class)
                .isThrownBy(() -> complete(client("test-key", Duration.ofSeconds(2)), "가".repeat(40_000)))
                .satisfies(e -> assertThat(e.getField()).isEqualTo("body"));
        assertThat(requestBody.get()).isNull();
    }

    @Test
    void reportsUnavailableWhenTheApiKeyIsMissing() {
        assertThatExceptionOfType(AiServiceException.class)
                .isThrownBy(() -> complete(client("", Duration.ofSeconds(2)), "project"))
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(requestBody.get()).isNull();
    }

    @Test
    void stopsWaitingForAStalledProvider() {
        stall = true;
        assertThatExceptionOfType(AiServiceException.class)
                .isThrownBy(() -> complete(client("test-key", Duration.ofMillis(300)), "project"))
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    }

    @Test
    void reportsAnUpstreamFailureForAnHttpErrorOrTruncatedOutput() {
        var client = client("test-key", Duration.ofSeconds(2));
        responseStatus = 429;
        assertThatExceptionOfType(AiServiceException.class)
                .isThrownBy(() -> complete(client, "project"))
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));

        responseStatus = 200;
        responseBody = completion("generated text", "length");
        assertThatExceptionOfType(AiServiceException.class)
                .isThrownBy(() -> complete(client, "project"))
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void rejectsDraftsWithBlankSectionsBeforeRendering() {
        responseBody = completion("""
                {"title":"GoPick","intro":"Project intro","sections":[
                    {"heading":" ","body":" "},{"heading":"Goal","body":"Build an app"}
                ],"wantedTeam":["Backend developer"]}
                """, "stop");
        var draft = new UpstageRecruitPostAiClient(client("test-key", Duration.ofSeconds(2)), mapper);
        var request = new RecruitPostDraftRequest("GoPick", null, null, null, null, null, null, null, null);

        assertThatExceptionOfType(AiServiceException.class)
                .isThrownBy(() -> draft.generate(request))
                .satisfies(e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    private UpstageChatClient client(String key, Duration readTimeout) {
        return new UpstageChatClient(mapper, key, "http://127.0.0.1:" + provider.getAddress().getPort() + "/v1",
                "test-model", Duration.ofSeconds(2), readTimeout);
    }

    private static String complete(UpstageChatClient client, String input) {
        return client.complete("system", input, 0.2, 128, Map.of("type", "json_object"));
    }

    private String completion(String text, String finishReason) {
        return mapper.writeValueAsString(Map.of("choices",
                List.of(Map.of("finish_reason", finishReason, "message", Map.of("content", text)))));
    }
}
