package com.gopick.server.project.service;

import com.gopick.server.project.dto.RecruitPostDraftRequest;
import com.gopick.server.project.exception.AiServiceException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

@Component
public class UpstageRecruitPostAiClient implements RecruitPostAiClient {

    private static final int MAX_OUTPUT_TOKENS = 2_048;

    private final UpstageChatClient chatClient;
    private final ObjectMapper objectMapper;

    public UpstageRecruitPostAiClient(UpstageChatClient chatClient, ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public String generate(RecruitPostDraftRequest request) {
        String userPrompt = """
                다음 프로젝트 정보를 바탕으로 팀원 모집글을 작성하세요.
                제공된 정보만 사용하고 지정된 출력 구조를 모두 채우세요.

                INPUT:
                %s
                """.formatted(objectMapper.writeValueAsString(request));
        String content = chatClient.complete(systemPrompt(), userPrompt, 0.4, MAX_OUTPUT_TOKENS, responseFormat());
        try {
            GeneratedPost generated = objectMapper.readValue(content, GeneratedPost.class);
            validateGeneratedPost(generated);
            return renderPost(generated);
        } catch (JacksonException | IllegalStateException e) {
            throw AiServiceException.upstreamFailure(e);
        }
    }

    private String systemPrompt() {
        return """
                당신은 개발 프로젝트의 팀원 모집글을 작성하는 도우미입니다.

                입력으로 제공된 프로젝트 정보만 사용하여
                자연스럽고 읽기 쉬운 한국어 모집글을 작성하세요.

                입력에 없는 프로젝트 내용, 기술, 경험, 일정,
                혜택, 능력, 성향 또는 사실을 임의로 만들어내지 않습니다.

                과장된 홍보 문구보다는
                실제 프로젝트 팀원을 모집하는 사람이 작성한 것처럼
                친근하고 명확한 말투를 사용합니다.

                [출력 구성]

                반드시 다음 네 가지 정보를 모두 생성합니다.

                1. title

                모집글의 첫 줄에 사용할 제목입니다.

                프로젝트명과 프로젝트 성격을 바탕으로
                간결하고 자연스럽게 작성합니다.


                2. intro

                제목 다음에 표시할 프로젝트 소개 한 문장입니다.

                프로젝트가 무엇을 만드는지 또는
                어떤 목적의 프로젝트인지 이해할 수 있도록 작성합니다.


                3. sections

                모집글 본문에 들어갈 문단들입니다.

                각 section은 heading과 body를 가집니다.

                입력에 있는 정보를 바탕으로 다음 내용을 활용합니다.

                - 프로젝트 추진 배경
                - 프로젝트 목표
                - 프로젝트 분야와 목적
                - 모집 역할과 기술 조건
                - 협업 방식

                최소 2개의 section을 작성합니다.

                입력에 충분한 정보가 있다면
                각 주제를 별도의 section으로 나누어 작성합니다.

                heading에는 "■" 문자를 넣지 않습니다.

                body는 해당 heading에 대한 내용을
                자연스러운 문장으로 작성합니다.

                서로 다른 정보를 억지로 하나의 문단에 몰아넣지 않습니다.


                4. wantedTeam

                모집글 마지막의
                "이런 분과 함께하고 싶어요"에 들어갈 목록입니다.

                원하는 팀원,
                모집 역할,
                기술 조건,
                협업 선호 등

                입력 데이터에서 확인 가능한 내용만 사용합니다.

                각 배열 항목은 최종 모집글에서
                한 줄의 목록으로 사용됩니다.

                각 항목 앞에 "-" 문자를 직접 넣지 않습니다.

                최소 1개 이상의 항목을 작성합니다.


                [중요 규칙]
                
                조건의 강도를 정확히 유지합니다.
                
                techMode가 PREFERRED인 기술은
                "필수", "해야 합니다", "사용할 수 있어야 합니다"처럼 표현하지 않고
                "우대합니다", "선호합니다", "~이면 좋습니다"처럼 표현합니다.
                
                techMode가 REQUIRED인 경우에만
                필수 조건으로 표현합니다.
                
                협업 방식 역시 입력이 선호 조건이라면
                "협업할 예정입니다"처럼 확정하지 않고
                "선호합니다", "~한 분이면 좋습니다"처럼 표현합니다.
                
                입력 데이터의 의미나 조건의 강도를 임의로 변경하지 않습니다.
                
                한국어 단어 내부에 불필요한 공백을 넣지 않습니다.
                예: "수 준", "개발 자"처럼 쓰지 않습니다.

                입력값을 그대로 기계적으로 이어 붙이지 말고,
                의미는 유지하면서 자연스러운 모집글 문장으로 다듬습니다.

                그러나 입력에 없는 내용을 추가해서는 안 됩니다.

                입력값이 비어 있는 정보는
                임의로 추측하여 채우지 않습니다.

                프로젝트에 없는 기술을 추가하지 않습니다.

                모집하지 않는 역할을 추가하지 않습니다.

                입력에 없는 경험이나 자격 조건을 추가하지 않습니다.

                입력에 없는 일정이나 혜택을 만들어내지 않습니다.
                
                입력에 있는 "선호"를 경험, 숙련도 또는 능력으로 바꾸어 표현하지 않습니다.
                예를 들어 "코드 리뷰를 선호함"을 "코드 리뷰에 익숙한 분"으로 바꾸지 않습니다.
                
                한국어 단어 내부나 문장 시작에 불필요한 공백을 넣지 않습니다.
                목록 기호 "- " 뒤에는 공백을 정확히 하나만 사용합니다.
                """;
    }

    private String renderPost(GeneratedPost generated) {

        StringBuilder post = new StringBuilder();

        post.append(generated.title().trim());

        post.append("\n\n");
        post.append(generated.intro().trim());

        for (GeneratedSection section : generated.sections()) {
            post.append("\n\n■ ");
            post.append(section.heading().trim());

            post.append("\n");
            post.append(section.body().trim());
        }

        post.append("\n\n■ 이런 분과 함께하고 싶어요");

        for (String item : generated.wantedTeam()) {
            post.append("\n- ");
            post.append(item.trim());
        }

        return post.toString();
    }

    private void validateGeneratedPost(GeneratedPost generated) {

        if (generated == null) {
            throw new IllegalStateException(
                    "생성된 모집글이 비어 있습니다."
            );
        }

        if (generated.title() == null
                || generated.title().isBlank()) {
            throw new IllegalStateException(
                    "생성된 모집글 제목이 없습니다."
            );
        }

        if (generated.intro() == null
                || generated.intro().isBlank()) {
            throw new IllegalStateException(
                    "생성된 모집글 소개가 없습니다."
            );
        }

        if (generated.sections() == null || generated.sections().size() < 2
                || generated.sections().stream().anyMatch(section -> section == null
                        || section.heading() == null || section.heading().isBlank()
                        || section.body() == null || section.body().isBlank())) {
            throw new IllegalStateException(
                    "생성된 모집글 본문이 부족합니다."
            );
        }

        if (generated.wantedTeam() == null || generated.wantedTeam().isEmpty()
                || generated.wantedTeam().stream().anyMatch(item -> item == null || item.isBlank())) {
            throw new IllegalStateException(
                    "생성된 모집글의 팀원 조건이 없습니다."
            );
        }
    }

    private Map<String, Object> responseFormat() {

        Map<String, Object> sectionSchema =
                Map.of(
                        "type", "object",

                        "properties", Map.of(
                                "heading", Map.of(
                                        "type", "string"
                                ),

                                "body", Map.of(
                                        "type", "string"
                                )
                        ),

                        "required", List.of(
                                "heading",
                                "body"
                        ),

                        "additionalProperties", false
                );

        Map<String, Object> rootSchema =
                Map.of(
                        "type", "object",

                        "properties", Map.of(
                                "title", Map.of(
                                        "type", "string"
                                ),

                                "intro", Map.of(
                                        "type", "string"
                                ),

                                "sections", Map.of(
                                        "type", "array",
                                        "items", sectionSchema
                                ),

                                "wantedTeam", Map.of(
                                        "type", "array",
                                        "items", Map.of(
                                                "type", "string"
                                        )
                                )
                        ),

                        "required", List.of(
                                "title",
                                "intro",
                                "sections",
                                "wantedTeam"
                        ),

                        "additionalProperties", false
                );

        return Map.of(
                "type", "json_schema",

                "json_schema", Map.of(
                        "name", "recruit_post",

                        "strict", true,

                        "schema", rootSchema
                )
        );
    }

    private record GeneratedPost(
            String title,
            String intro,
            List<GeneratedSection> sections,
            List<String> wantedTeam
    ) {}

    private record GeneratedSection(
            String heading,
            String body
    ) {}
}
