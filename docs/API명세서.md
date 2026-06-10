# API 명세서 — 학습자료 기반 스터디 챗봇

> 프론트엔드와 백엔드가 통신하는 규약. RESTful 원칙(자원=URL, 행동=HTTP 메서드)을 따른다.
> **현재 실제 구현된 엔드포인트 기준**으로 작성되었다.
>
> 📑 실시간 API 문서는 서버 기동 후 **Swagger UI(`/swagger-ui.html`)** 에서도 확인할 수 있다.

---

## 0. 공통 사항

### HTTP 메서드
| 메서드 | 의미 |
|--------|------|
| `GET` | 조회 |
| `POST` | 생성·실행 |
| `DELETE` | 삭제 |

### 주요 상태 코드
| 코드 | 의미 |
|------|------|
| `200` | 성공 |
| `201` | 생성 성공 |
| `204` | 성공(응답 본문 없음) |
| `400` | 잘못된 요청 (입력값 검증 실패 등) |
| `401` | 인증 안 됨 (토큰 없음·만료·무효) |
| `403` | 권한 없음 (남의 자원 접근 등) |
| `404` | 자원 없음 |
| `500` | 서버 오류 |

> `401`(미인증)과 `403`(인가 실패)은 의미가 구분된다. 토큰이 없거나 유효하지 않으면 `401`, 인증은 됐지만 접근 권한이 없으면 `403`을 반환한다.

### 인증 방식
- 로그인 후 발급받은 **액세스 토큰**을 요청 헤더에 담아 보낸다.
  `Authorization: Bearer {accessToken}`
- 액세스 토큰 만료 시 **리프레시 토큰**으로 재발급받는다.
- 인증이 필요 없는 경로: `/api/auth/**`, Swagger UI 관련 경로. **그 외 모든 API는 액세스 토큰이 필요하다.**

### 응답 래퍼
성공 응답은 일관되게 `data` 필드로 감싼 형태(`ApiResponse`)로 내려간다.

```json
{ "data": { ... } }
```

> 예외: `DELETE /api/documents/{id}`는 본문 없이 `204`를 반환하고, `POST /api/chat/stream`은 SSE 스트림이라 래퍼를 사용하지 않는다.

### 에러 응답 포맷
모든 에러는 전역 예외 핸들러를 통해 일관된 형식으로 반환된다.

```json
{
  "code": "에러 코드",
  "message": "사람이 읽을 메시지",
  "errors": [
    { "field": "email", "message": "올바른 이메일 형식이 아닙니다." }
  ]
}
```

> `errors`는 입력값 검증 실패 시에만 채워지며, 그 외에는 생략될 수 있다.

---

## 1. 인증 (Auth)

> 모두 인증 불필요(`/api/auth/**`).

### POST `/api/auth/signup` — 회원가입
- **요청**: `email`, `password`(8~100자), `name`(~100자)
- **응답 (201)**: `data` = `{ id, email, name, createdAt }`

### POST `/api/auth/login` — 로그인
- **요청**: `email`, `password`
- **응답 (200)**: `data` = `{ accessToken, refreshToken, tokenType: "Bearer" }`

### POST `/api/auth/refresh` — 액세스 토큰 재발급
- **요청**: `refreshToken`
- **응답 (200)**: `data` = `{ accessToken, tokenType: "Bearer" }`

### POST `/api/auth/logout` — 로그아웃
- **요청**: 헤더 `Authorization: Bearer {accessToken}` (본문 없음)
- **동작**: Redis에 저장된 리프레시 토큰을 삭제해 무효화한다.
- **응답 (200)**: `data` = `"로그아웃되었습니다."`

---

## 2. 사용자 (User)

> 인증 필요.

### GET `/api/users/me` — 내 정보 조회
- **요청**: 없음 (토큰으로 사용자 식별)
- **응답 (200)**: `data` = `{ id, email, name }`

---

## 3. 학습자료 (Documents)

> 모두 인증 필요. 자료는 토큰의 사용자 소유로 귀속되며, 남의 자료 접근 시 `403`/`404`.

### POST `/api/documents` — 텍스트 자료 업로드
- **요청 (JSON)**: `title`(~255자), `content`(본문 텍스트)
- **동작**: 자료를 저장하고 청킹·임베딩하여 Redis 벡터 스토어에 적재한다.
- **응답 (201)**: `data` = `{ id, title, status, createdAt }`

### POST `/api/documents/pdf` — PDF 자료 업로드
- **요청 (multipart/form-data)**: `file`(PDF, 필수), `title`(선택)
- **제약**: PDF만 허용(`application/pdf`). 최대 업로드 크기 **30MB**.
- **동작**: PDF에서 텍스트를 추출한 뒤 텍스트 업로드와 동일하게 처리한다.
- **응답 (201)**: `data` = `{ id, title, status, createdAt }`

### GET `/api/documents` — 내 자료 목록 조회
- **응답 (200)**: `data` = `[{ id, title, status, createdAt }, ...]`

### GET `/api/documents/{id}` — 자료 상세·처리 상태 조회
- **응답 (200)**: `data` = `{ id, title, status, createdAt }`

### DELETE `/api/documents/{id}` — 자료 삭제
- **동작**: 자료 삭제 시 Redis의 관련 벡터 청크도 함께 정리한다.
- **응답 (204)**: 본문 없음

> `status` 값: `PROCESSING`(처리 중) / `DONE`(완료) / `FAILED`(실패)

---

## 4. 챗봇 (Chat) — RAG

> 모두 인증 필요. 질문 대상 자료가 본인 소유가 아니면 `403`/`404`.

### POST `/api/chat` — 질문 (동기 응답)
- **요청 (JSON)**: `documentId`, `message`
- **동작**: 질문과 유사한 자료 청크를 벡터 검색해 근거로 결합한 뒤 LLM 답변을 생성한다.
- **응답 (200)**: `data` = `{ answer }` (완성된 답변을 한 번에 반환)

### POST `/api/chat/stream` — 질문 (SSE 스트리밍)
- **요청 (JSON)**: `documentId`, `message`
- **응답**: `text/event-stream` (SSE). LLM이 생성하는 답변 토큰을 조각 단위로 순차 전송한다.
- **참고**:
  - `EventSource`는 커스텀 헤더를 지원하지 않으므로 `fetch()` + `Authorization` 헤더로 호출한다.
  - 검증·RAG 검색 단계에서 발생하는 오류(`403`/`404` 등)는 스트림 시작 전에 일반 HTTP 오류로 반환된다.
  - 서버 타임아웃은 3분(180초)이다.

---

## 5. 퀴즈 (Quiz)

> 모두 인증 필요. 세션은 토큰의 사용자 소유로 귀속되며, 남의 세션 접근 시 `403`/`404`.

### POST `/api/quiz/generate` — 퀴즈 생성
- **요청 (JSON)**: `documentId`, `questionCount`(**3, 5, 10 중 하나**)
- **동작**: 자료를 근거로 4지선다 문제를 생성해 세션으로 저장한다.
- **응답 (200)**: `data` = `{ sessionId, questions: [{ id, questionOrder, question, choices }] }`
- **주의**: 생성 응답에는 **정답(`answerIndex`)·해설(`explanation`)이 포함되지 않는다** (풀기 전 정답 노출 방지).

### POST `/api/quiz/{sessionId}/submit` — 답안 제출·채점
- **요청 (JSON)**: `answers`: `[{ questionId, selectedIndex(0~3) }, ...]`
- **동작**: 채점 후 세션에 점수·제출 시각을 기록한다. **이 응답부터 정답·해설을 공개한다.**
- **응답 (200)**: `data` = `{ score, totalCount, results: [{ questionId, questionOrder, question, choices, answerIndex, userAnswerIndex, correct, explanation }] }`

### GET `/api/quiz/sessions` — 내 퀴즈 세션 목록 조회
- **응답 (200)**: `data` = `[{ sessionId, documentTitle, questionCount, score, createdAt, submittedAt }, ...]`
- `score`·`submittedAt`이 null이면 아직 제출하지 않은 세션이다.

### GET `/api/quiz/sessions/{sessionId}` — 세션 상세 조회
- **응답 (200)**: `data` = `{ sessionId, documentTitle, questionCount, score, createdAt, submittedAt, questions: [{ questionId, questionOrder, question, choices, answerIndex, userAnswerIndex, correct, explanation }] }`
- 상세 조회는 정답·해설을 포함한다. 미제출 세션이면 각 문제의 `userAnswerIndex`·`correct`가 null로 내려간다.

---

## 6. 비기능 관련 규약

- **에러 응답 포맷**: 모든 에러는 전역 예외 핸들러를 통해 [공통 사항](#에러-응답-포맷)의 형식으로 반환한다.
- **인증 필요 범위**: `/api/auth/**`와 Swagger UI 경로를 제외한 모든 API는 액세스 토큰이 필요하다.
- **무상태(stateless)**: 서버 세션을 두지 않고 JWT로 인증한다(`SessionCreationPolicy.STATELESS`).

---

## 7. 향후 확장 (미구현)

다음 API는 아직 구현되지 않았으며, 확장 단계에서 추가를 검토한다.

- 대화 기록 저장·조회 (conversations / messages)
- 소셜 로그인 (구글·카카오 OAuth 2.0)
- 챗봇 질문 rate limiting (`429 Too Many Requests`)

---

*이 문서는 살아있는 문서로, 개발 진행에 따라 갱신된다. 요청/응답의 상세 필드는 Swagger(OpenAPI)에서 함께 확인할 수 있다.*
