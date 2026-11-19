package com.gopick.server.project.service;

import com.gopick.server.project.dto.RecommendationAiRequest;
import com.gopick.server.project.dto.RecommendationAiResult;
import com.gopick.server.project.exception.AiServiceException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(
        name = "recommendation.ai.mode",
        havingValue = "upstage"
)
public class UpstageRecommendationAiClient
        implements RecommendationAiClient {

    private static final int MAX_OUTPUT_TOKENS = 8_192;

    private final UpstageChatClient chatClient;
    private final ObjectMapper objectMapper;

    public UpstageRecommendationAiClient(UpstageChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public RecommendationAiResult recommend(RecommendationAiRequest request) {
        request = RecommendationExperiencePolicy.sanitizeRequest(request);
        String userPrompt = """
                다음은 프로젝트 정보와 추천 가능한 후보 목록입니다.
                제공된 데이터만 사용해서 모든 후보를 비교하고 추천 결과를 생성하세요.

                INPUT:
                %s
                """.formatted(objectMapper.writeValueAsString(request));
        String content = chatClient.complete(systemPrompt(), userPrompt, 0.2, MAX_OUTPUT_TOKENS, responseFormat());
        try {
            RecommendationAiResult result = objectMapper.readValue(content, RecommendationAiResult.class);
            validateResult(request, result);
            return RecommendationExperiencePolicy.sanitizeResult(request, result);
        } catch (JacksonException | IllegalStateException e) {
            throw AiServiceException.upstreamFailure(e);
        }
    }

    private String systemPrompt() {
        return """
            당신은 프로젝트 팀원 추천 시스템입니다.

            프로젝트 정보와 후보 프로필을 비교하여
            모든 후보의 프로젝트 적합도를 평가하세요.

            [기본 규칙]

            1. 반드시 입력으로 제공된 후보만 평가합니다.
            2. 후보를 추가하거나 삭제하지 않습니다.
            3. 모든 후보를 정확히 한 번씩 결과에 포함합니다.
            4. score는 0~100 정수입니다.
            5. 적합도가 같다면 동일한 score를 사용할 수 있습니다.
            6. score가 높을수록 프로젝트에 더 적합한 후보입니다.
            7. REQUIRED 조건은 서버에서 이미 검증했습니다.
               따라서 REQUIRED 조건을 이유로 후보를 제거하지 않습니다.
            8. experienceMode가 NONE이면 후보의 경험이나 경력을 점수, 순위,
               matched, unmatched, summary의 판단 근거로 사용하지 않습니다.
               경험이나 경력을 추측하지 않으며 EXPERIENCE 항목을 출력하지 않습니다.

            [추천 판단]

            score와 summary는 프로젝트와 후보의 전체 정보를 종합하여 판단합니다.

            단순히 일치 항목의 개수를 세거나 고정 점수를 더하지 말고,
            기술 스택, 협업 방식, 프로젝트 경험, 희망 역할, 관심 분야 등
            프로젝트 수행에 실제로 중요한 요소들을 종합적으로 비교하세요.

            중요도는 다음 순서를 우선적으로 고려합니다.

            1. STACK
            2. COLLAB
            3. EXPERIENCE
            4. ROLE
            5. FIELD

            그 외 PURPOSE, MAJOR, PART도
            입력 정보상 실제 관련성이 있을 경우 고려할 수 있습니다.

            [matched / unmatched 작성 규칙]

            matched와 unmatched는 AI의 추측이 아니라
            입력 데이터에서 확인 가능한 사실을 근거로 작성합니다.

            matched에는 프로젝트 조건과 후보 정보가
            실제로 일치하거나 명확하게 부합하는 항목만 포함합니다.

            단순히 관련성이 있거나 도움이 될 수 있다는 이유만으로
            matched에 포함하지 않습니다.

            unmatched에는 프로젝트 조건과 후보 정보가
            명확하게 다르거나 요구 조건에 비해 부족한 경우를 포함합니다.

            예시:
            - 프로젝트 협업 방식: "코드 리뷰"
              후보 선호: "코드 리뷰"
              → matched

            - 프로젝트 협업 방식: "코드 리뷰"
              후보 선호: "문서화"
              → unmatched

            - 프로젝트 기술: Java Lv.2 이상 선호
              후보 기술: Java Lv.3
              → matched

            - 프로젝트 기술: Java 선호
              후보 기술: Python만 보유
              → unmatched

            입력 데이터에 없는 의지, 성격, 능력, 가능성,
            경험 또는 성향을 추측하거나 만들어내지 않습니다.

            예를 들어 입력에 근거가 없다면
            "협업 의지가 있다",
            "빠르게 적응할 수 있다",
            "성장 가능성이 높다"
            같은 표현을 사용하지 않습니다.

            [출력 문구]

            category는 반드시 다음 중 하나만 사용합니다.

            STACK
            COLLAB
            EXPERIENCE
            ROLE
            FIELD
            PURPOSE
            MAJOR
            PART

            matched와 unmatched의 text는
            짧고 구체적인 한국어 구문으로 작성합니다.
            가능하면 한 항목당 30자 내외로 작성합니다.

            summary는 후보의 장점과 부족한 점을 함께 고려하여
            추천 순위를 이해할 수 있도록 한 문장으로 작성합니다.

            summary에서는 전체 맥락을 종합적으로 설명할 수 있지만,
            입력에 없는 사실을 새롭게 만들어내면 안 됩니다.
            
            같은 category의 근거가 여러 개일 경우 가능한 한 하나의 핵심 근거로 합쳐 작성한다.
            """;
    }

    private Map<String, Object> responseFormat() {

        Map<String, Object> reasonSchema =
                Map.of(
                        "type", "object",

                        "properties", Map.of(
                                "category", Map.of(
                                        "type", "string",
                                        "enum", List.of(
                                                "STACK",
                                                "COLLAB",
                                                "EXPERIENCE",
                                                "ROLE",
                                                "FIELD",
                                                "PURPOSE",
                                                "MAJOR",
                                                "PART"
                                        )
                                ),

                                "text", Map.of(
                                        "type",
                                        "string"
                                )
                        ),

                        "required",
                        List.of(
                                "category",
                                "text"
                        ),

                        "additionalProperties",
                        false
                );

        Map<String, Object> itemSchema =
                Map.of(
                        "type", "object",

                        "properties", Map.of(
                                "profileId", Map.of(
                                        "type", "integer"
                                ),

                                "score", Map.of(
                                        "type", "integer",
                                        "minimum", 0,
                                        "maximum", 100
                                ),

                                "matched", Map.of(
                                        "type", "array",
                                        "items", reasonSchema
                                ),

                                "unmatched", Map.of(
                                        "type", "array",
                                        "items", reasonSchema
                                ),

                                "summary", Map.of(
                                        "type",
                                        List.of(
                                                "string",
                                                "null"
                                        )
                                )
                        ),

                        "required",
                        List.of(
                                "profileId",
                                "score",
                                "matched",
                                "unmatched",
                                "summary"
                        ),

                        "additionalProperties",
                        false
                );

        Map<String, Object> rootSchema =
                Map.of(
                        "type", "object",

                        "properties", Map.of(
                                "items", Map.of(
                                        "type", "array",
                                        "items", itemSchema
                                )
                        ),

                        "required",
                        List.of("items"),

                        "additionalProperties",
                        false
                );

        return Map.of(
                "type",
                "json_schema",

                "json_schema",
                Map.of(
                        "name",
                        "project_member_recommendations",

                        "strict",
                        true,

                        "schema",
                        rootSchema
                )
        );
    }

    private void validateResult(
            RecommendationAiRequest request,
            RecommendationAiResult result
    ) {
        if (result == null || result.items() == null || result.items().stream()
                .anyMatch(item -> item == null || item.profileId() == null)) {

            throw new IllegalStateException(
                    "AI 추천 결과가 비어 있습니다."
            );
        }

        Set<Long> requestedIds =
                request.candidates()
                        .stream()
                        .map(
                                RecommendationAiRequest
                                        .Candidate::profileId
                        )
                        .collect(Collectors.toSet());

        List<Long> returnedIds =
                result.items()
                        .stream()
                        .map(
                                RecommendationAiResult
                                        .Item::profileId
                        )
                        .toList();

        Set<Long> returnedIdSet =
                Set.copyOf(returnedIds);

        /*
         * 중복 여부
         */
        if (returnedIds.size()
                != returnedIdSet.size()) {

            throw new IllegalStateException(
                    "AI가 동일 후보를 중복 반환했습니다."
            );
        }

        /*
         * 모든 후보가 정확히 한 번씩
         * 반환됐는지 확인
         */
        if (!requestedIds.equals(
                returnedIdSet
        )) {

            throw new IllegalStateException(
                    "AI 추천 결과의 후보 목록이 요청 후보와 일치하지 않습니다."
            );
        }

        /*
         * 혹시 structured output 검증 밖에서
         * 이상한 점수가 들어온 경우 추가 방어
         */
        boolean invalidScore =
                result.items()
                        .stream()
                        .anyMatch(item ->
                                item.score() < 0
                                        ||
                                        item.score() > 100
                        );

        if (invalidScore) {
            throw new IllegalStateException(
                    "AI 추천 점수 범위가 올바르지 않습니다."
            );
        }
    }
}
