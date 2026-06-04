# API 명세서 — 학습자료 기반 스터디 챗봇

> 프론트엔드와 백엔드가 통신하는 규약. RESTful 원칙(자원=URL, 행동=HTTP 메서드)을 따른다.

---

## 0. 공통 사항

### HTTP 메서드
| 메서드 | 의미 |
|--------|------|
| `GET` | 조회 |
| `POST` | 생성 |
| `PUT` / `PATCH` | 수정 |
| `DELETE` | 삭제 |

### 주요 상태 코드
| 코드 | 의미 |
|------|------|
| `200` | 성공 |
| `201` | 생성 성공 |
| `400` | 잘못된 요청 (입력값 오류 등) |
| `401` | 인증 안 됨 (토큰 없음·만료) |
| `403` | 권한 없음 |
| `404` | 자원 없음 |
| `500` | 서버 오류 |

### 인증 방식
- 로그인 후 발급받은 **액세스 토큰**을 요청 헤더에 담아 보낸다.
  `Authorization: Bearer {access_token}`
- 액세스 토큰 만료 시 **리프레시 토큰**으로 재발급받는다.

### 경로 규칙 (RESTful)
- 모든 경로는 `/api/`로 시작한다.
- 자원 이름은 복수형을 사용한다. (`documents`, `quizzes`)
- 특정 항목은 `/{id}`로 가리킨다. (`/api/documents/3`)

---

## 1. 인증 (Auth) — 핵심

| 메서드 | 경로 | 설명 | 요청 본문 | 응답 |
|--------|------|------|-----------|------|
| POST | `/api/auth/signup` | 회원가입 | `email`, `password`, `name` | 가입된 사용자 정보 (201) |
| POST | `/api/auth/login` | 로그인 | `email`, `password` | `access_token`, `refresh_token` (200) |
| POST | `/api/auth/refresh` | 토큰 재발급 | `refresh_token` | 새 `access_token` (200) |
| POST | `/api/auth/logout` | 로그아웃 | (헤더 토큰) | 성공 메시지 (200) |

---

## 2. 학습자료 (Documents) — 핵심

| 메서드 | 경로 | 설명 | 요청 | 응답 |
|--------|------|------|------|------|
| POST | `/api/documents` | 자료 업로드 | 파일(multipart, PDF) | 자료 `id`, `status` (201) |
| GET | `/api/documents` | 내 자료 목록 조회 | - | 자료 목록 (200) |
| GET | `/api/documents/{id}` | 자료 상세·처리 상태 조회 | - | 자료 정보, `status` (200) |
| DELETE | `/api/documents/{id}` | 자료 삭제 (조각·벡터 연쇄 삭제) | - | 성공 메시지 (200) |

> `status` 값: `processing`(처리 중) / `done`(완료) / `failed`(실패)

---

## 3. 챗봇 (Chat) — 핵심

| 메서드 | 경로 | 설명 | 요청 | 응답 |
|--------|------|------|------|------|
| POST | `/api/chat` | 질문하기 | `document_id`, `message` | WebSocket으로 답변 스트리밍 |
| GET | `/api/conversations` | 대화 목록 조회 | - | 대화 목록 (200) |
| GET | `/api/conversations/{id}` | 특정 대화 내역 조회 | - | 메시지 목록 (200) |

> 챗봇 답변은 일반 HTTP 응답이 아니라 **WebSocket**을 통해 한 글자씩(토큰 단위) 스트리밍된다.
> WebSocket 연결 경로 및 메시지 포맷은 개발 단계에서 구체화한다.

---

## 4. 퀴즈 (Quiz) — 확장

| 메서드 | 경로 | 설명 | 요청 | 응답 |
|--------|------|------|------|------|
| POST | `/api/quizzes` | 퀴즈 생성 | `document_id` | 퀴즈 `id`, 문제 목록 (201) |
| GET | `/api/quizzes/{id}` | 퀴즈 조회 | - | 문제 목록 (200) |
| POST | `/api/quizzes/{id}/submit` | 퀴즈 풀이 제출 | 답안 목록 | 채점 결과 (200) |
| GET | `/api/quizzes/attempts` | 내 풀이 기록 조회 | - | 풀이 기록 (200) |

---

## 5. 소셜 로그인 (Social) — 확장

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/api/auth/oauth/google` | 구글 로그인 시작 (리다이렉트) |
| GET | `/api/auth/oauth/kakao` | 카카오 로그인 시작 (리다이렉트) |
| GET | `/api/auth/oauth/callback` | 소셜 로그인 콜백 처리, 토큰 발급 |

> OAuth 2.0 흐름. 소셜 로그인 성공 시 자체 로그인과 동일하게 JWT 토큰을 발급한다.

---

## 6. 비기능 관련 규약

- **요청 제한(Rate Limiting)**: 챗봇 질문(`POST /api/chat`)은 사용자당 분당 호출 횟수를 제한한다. 초과 시 `429 Too Many Requests`를 반환한다.
- **에러 응답 포맷**: 모든 에러는 일관된 형식으로 반환한다. (예: `{ "code": ..., "message": ... }` — 구체 포맷은 개발 단계에서 확정)
- **인증 필요 범위**: 인증(signup/login/refresh)과 소셜 로그인 시작을 제외한 모든 API는 액세스 토큰이 필요하다.

---

*이 문서는 살아있는 문서로, 개발 진행에 따라 갱신된다. 실제 요청/응답의 상세 필드는 구현 시 Swagger(OpenAPI)로 자동 문서화하는 것을 권장한다.*
