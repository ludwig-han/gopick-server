package com.gopick.server.project.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gopick.server.project.exception.AiServiceException;
import com.gopick.server.project.exception.ProjectValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
class UpstageChatClient {
    private static final int MAX_REQUEST_BYTES = 96 * 1024;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String model;
    private final boolean configured;

    UpstageChatClient(ObjectMapper objectMapper,
                      @Value("${upstage.api-key:}") String apiKey,
                      @Value("${upstage.base-url:https://api.upstage.ai/v1}") String baseUrl,
                      @Value("${upstage.model:solar-pro4}") String model,
                      @Value("${upstage.connect-timeout:5s}") Duration connectTimeout,
                      @Value("${upstage.read-timeout:20s}") Duration readTimeout) {
        this.objectMapper = objectMapper;
        this.model = model;
        this.configured = !apiKey.isBlank();
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    String complete(String systemPrompt, String userPrompt, double temperature, int maxTokens,
                    Map<String, Object> responseFormat) {
        if (!configured) {
            throw AiServiceException.unavailable();
        }

        byte[] body = objectMapper.writeValueAsBytes(Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", temperature,
                "max_tokens", maxTokens,
                "response_format", responseFormat
        ));
        if (body.length > MAX_REQUEST_BYTES) {
            throw new ProjectValidationException("body", "AI 요청에 포함된 정보가 너무 많습니다.");
        }

        ChatResponse response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientException e) {
            if (e.getMostSpecificCause() instanceof SocketTimeoutException) {
                throw AiServiceException.timeout(e);
            }
            throw AiServiceException.upstreamFailure(e);
        }

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw AiServiceException.upstreamFailure(null);
        }
        Choice choice = response.choices().getFirst();
        if (choice == null || choice.message() == null || choice.message().content() == null
                || choice.message().content().isBlank() || "length".equals(choice.finishReason())) {
            throw AiServiceException.upstreamFailure(null);
        }
        return choice.message().content();
    }

    private record ChatResponse(List<Choice> choices) {}

    private record Choice(@JsonProperty("finish_reason") String finishReason, Message message) {}

    private record Message(String content) {}
}
