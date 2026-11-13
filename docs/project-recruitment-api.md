# 프로젝트 모집 정보

로그인한 사용자는 `POST /projects`에서 기존 프로젝트 생성 필드와 모집 정보를 함께 저장할 수 있습니다.
팀장은 `PATCH /projects/{projectId}`에서 같은 필드를 수정할 수 있습니다. 다른 구성원의 수정은
`403 PROJECT_ACCESS_DENIED`를 반환합니다.

다음은 생성 요청 예시입니다. 기존 생성 요청은 모집 정보 없이도 사용할 수 있습니다.

```json
{
  "title": "GoPick",
  "role": "PM",
  "recruiting": true,
  "attributes": [
    {"category": "FIELD", "value": "WEB"},
    {"category": "PURPOSE", "value": "포트폴리오"},
    {"category": "COLLAB", "value": "코드 리뷰 선호"}
  ],
  "experience": {"mode": "PREFERRED", "types": ["HACKATHON"], "fields": ["WEB"]},
  "recruitments": [
    {
      "role": "BACKEND",
      "remainingCount": 2,
      "techMode": "REQUIRED",
      "technologies": [{"technology": "Java", "minLevel": 3}]
    }
  ],
  "links": [{"type": "GitHub", "url": "https://github.com/example/gopick"}]
}
```

- 모집 역할은 기존 `ProjectRole` 값이며 역할별로 한 항목을 저장합니다.
- `remainingCount`는 0 이상의 정수입니다. 기술별 `minLevel`은 프로필 숙련도와 같은 1~4 범위입니다.
- 기술과 경험 조건에는 `NONE`, `PREFERRED`, `REQUIRED`를 사용합니다. 기술 모드가 `NONE`이면 기술 목록을 비워 두고, 필수·우대라면 한 가지 이상 입력합니다.
- 링크 주소는 호스트가 있는 HTTP 또는 HTTPS URL이어야 합니다.
- 수정 요청에 포함한 목록은 전체를 교체합니다. 빈 배열로 목록을 비우고, 생략한 필드는 유지합니다. 경험 조건은 `{"mode":"NONE"}`으로 해제합니다.

생성 성공 시 `201 Created`와 `projectId`, 수정 성공 시 `200 OK`와 기본 정보 DTO를 반환합니다.
`GET /projects/{projectId}`에서 모집 정보 네 필드를 기존 상세 응답과 함께 확인할 수 있습니다.
조건 검증에 실패하면 `400 VALIDATION_ERROR`를 반환하고 같은 요청의 변경 사항은 반영되지 않습니다.
