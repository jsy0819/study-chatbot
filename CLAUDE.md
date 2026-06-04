# CLAUDE.md — 프로젝트 작업 규칙

> 이 파일은 Claude Code가 작업 시 자동으로 읽는 프로젝트 규칙서다.
> 코드 작성·수정 전 반드시 이 규칙을 따른다.

---

## 프로젝트 개요

- **서비스**: 학습자료 기반 스터디 챗봇 (RAG 기반)
- **설명**: 사용자가 올린 PDF·강의자료를 근거로 질문에 답하고, 퀴즈를 자동 생성하는 서비스
- **상세 문서**: `docs/` 폴더 참조 (요구사항정의서, ERD, API명세서)

---

## 기술 스택

### 백엔드 (`backend/`)
- Java 21 (LTS)
- Spring Boot 3.5.x (안정 GA 버전)
- Spring AI 1.x (정식 GA, RAG 구현)
- PostgreSQL (일반 데이터)
- Redis (벡터 스토어 · 세션 · 캐시)
- 빌드: Gradle (Gradle Wrapper 사용)
- 인증: JWT + 리프레시 토큰 (Spring Security)
- API 문서화: springdoc-openapi (Swagger)

### 프론트엔드 (`frontend/`)
- TypeScript
- React
- Vite (빌드 도구)

### 인프라
- Docker / docker-compose (로컬에서 Redis 등 실행)

---

## 핵심 아키텍처 원칙

- 모든 요청은 Spring Boot 서버를 거친다. 프론트엔드가 DB나 외부 LLM API에 직접 접근하지 않는다.
- 인증·검증·비즈니스 로직은 백엔드에 집중한다.
- 챗봇 답변은 WebSocket으로 실시간 스트리밍한다.
- RAG 흐름: 질문 → 관련 자료 벡터 검색(Redis) → 질문+자료 결합 → LLM 호출 → 스트리밍 응답.

---

## 개발 우선순위 (워킹 스켈레톤)

기능을 한 번에 다 만들지 않고, 작동하는 최소 단위부터 단계적으로 확장한다.

- **1단계 (뼈대)**: 자체 로그인(JWT) → 자료 업로드 → 단순 Q&A 1회 관통 (스트리밍·퀴즈 없이)
- **2단계 (핵심)**: PDF 추출, WebSocket 스트리밍, 리프레시 토큰, 자료 처리 상태 표시
- **3단계 (확장)**: 퀴즈, 시맨틱 캐싱, rate limiting, 대화 기록, 소셜 로그인(구글→카카오)

현재 단계를 벗어나는 기능을 미리 만들지 않는다.

---

## 코딩 컨벤션

### 공통
- 변수·함수: camelCase / 클래스·타입: PascalCase / 상수: UPPER_SNAKE_CASE
- 의미 있는 이름을 쓴다. 축약어 남발 금지.
- 한 함수는 한 가지 일만 한다.

### 백엔드 (Java / Spring)
- 계층 구조를 지킨다: Controller → Service → Repository
- 비즈니스 로직은 Service에 둔다. Controller는 얇게 유지한다.
- DTO를 사용해 엔티티를 직접 노출하지 않는다.
- 입력값 검증(validation)을 반드시 거친다.

### 프론트엔드 (TypeScript / React)
- TypeScript strict 모드를 따른다. `any` 사용을 피한다.
- 컴포넌트는 함수형으로 작성한다.
- 컴포넌트 파일: PascalCase.tsx / 그 외: kebab-case

---

## REST API 규칙

- 경로는 `/api/`로 시작한다.
- 자원 이름은 복수형을 쓴다 (`/api/documents`).
- 특정 항목은 `/{id}`로 가리킨다.
- 행동은 HTTP 메서드로 구분한다 (GET 조회 / POST 생성 / PUT·PATCH 수정 / DELETE 삭제).
- 상세 명세는 `docs/API명세서.md` 참조.

---

## 보안 규칙 (필수)

- 비밀번호는 반드시 해싱하여 저장한다 (BCrypt). 평문 저장 절대 금지.
- 시크릿(API 키, DB 비밀번호, JWT 시크릿 등)은 코드·저장소에 절대 포함하지 않는다.
- 모든 시크릿은 `.env` / 환경변수로 관리한다. `.env`는 절대 커밋하지 않는다 (`.gitignore`에 포함).
- 리프레시 토큰은 Redis에 저장한다.
- 챗봇 질문에 rate limiting을 적용한다 (LLM 비용 보호).

---

## Git / 브랜치 전략

- 커밋 메시지는 Conventional Commits를 따른다:
  - `feat:` 기능 추가 / `fix:` 버그 수정 / `docs:` 문서 / `refactor:` 리팩토링 / `chore:` 설정·잡일 / `test:` 테스트
  - 메시지는 한국어로 작성 가능, 명령형으로.
- **기능 하나 = 브랜치 하나** 원칙.
  - `main`: 항상 작동하는 안정 버전만 유지
  - `feature/기능명`: 기능 개발용 (예: `feature/auth`, `feature/document-upload`)
  - 기능 완성 후 `main`에 병합
- `main`에 직접 작업하지 않는다 (문서 단계는 예외).

---

## 에러 처리

- 오류 응답 형식을 일관되게 통일한다 (전역 예외 핸들러 사용).
- 예외 상황(자료 없음, 처리 실패 등)을 명시적으로 처리한다.

---

## 작업 방식 (Claude Code에게)

- 큰 변경이나 새 기능 구현 전에는 먼저 계획을 설명하고 확인을 받는다.
- 한 번에 한 기능씩 구현한다.
- 모르는 라이브러리·최신 버전은 추측하지 말고 확인 후 사용한다.
- 코드에 왜 그렇게 했는지 핵심 주석을 남긴다 (특히 설계 결정).
- 기존 코드 스타일과 구조를 따른다.
