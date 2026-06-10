# 📚 학습자료 기반 스터디 챗봇

> 학습 자료(텍스트·PDF)를 업로드하면 **그 자료를 근거로** 질문에 답하고(RAG), 자료 기반 퀴즈를 자동 생성·채점해 주는 스터디 챗봇입니다.

일반 AI 챗봇과 달리 내가 올린 자료에서 검색한 내용만 근거로 답하므로, 답변이 자료 범위 안에서 일관됩니다. 개인 포트폴리오 프로젝트입니다.

---

## ✨ 주요 기능

- **회원가입 / JWT 인증** — BCrypt 비밀번호 해싱, 액세스 토큰 + 리프레시 토큰. 리프레시 토큰은 Redis에 저장하고 로그아웃 시 무효화합니다.
- **자료 업로드 & 임베딩** — 텍스트 직접 입력과 PDF 업로드(최대 30MB)를 지원합니다. 업로드한 자료를 청킹·임베딩하여 Redis 벡터 스토어에 저장합니다.
- **RAG 기반 Q&A** — 질문과 유사한 자료 청크를 벡터 검색으로 찾아, 그 내용을 근거로 LLM이 답변을 생성합니다.
- **SSE 응답 스트리밍** — 답변을 한 조각씩 실시간으로 흘려보내(`/api/chat/stream`) 화면에 점진적으로 표시합니다.
- **자료 기반 퀴즈** — 자료로 4지선다 퀴즈를 생성하고, 제출하면 채점 결과와 정답·해설을 보여주며, 지난 퀴즈 세션을 기록·조회할 수 있습니다.

---

## 🛠 기술 스택

확인된 실제 버전 기준입니다.

### 백엔드 (`backend/`)
- **Java 21**, **Spring Boot 3.5.14** (Gradle Wrapper 빌드)
- **Spring AI 1.1.7** — Google GenAI(Gemini) ChatClient·임베딩, Redis VectorStore 스타터
- Spring Web / Spring Data JPA / Spring Security
- **JWT** — jjwt 0.13.0
- **Apache PDFBox 3.0.7** — PDF 텍스트 추출
- **springdoc-openapi 2.8.17** — Swagger UI API 문서

### 프론트엔드 (`frontend/`)
- **React 19**, **TypeScript 6**, **Vite 8**
- React Router 7, Axios, Tailwind CSS 4

### 인프라
- **Docker Compose** — PostgreSQL 16 (`5432`), Redis Stack 7.4 (`6379`, RediSearch 모듈 포함)

### AI
- **Gemini** — 채팅 모델 `gemini-2.5-flash`, 임베딩 모델 `gemini-embedding-001` (3072차원)

---

## 🧩 아키텍처

모든 요청은 Spring Boot 서버를 거치며, 프론트엔드는 DB나 외부 LLM API에 직접 접근하지 않습니다. 인증·검증·비즈니스 로직은 백엔드에 집중됩니다.

```
[React (Vite)]
      │  HTTP + SSE
      ▼
[Spring Boot 서버]
   │           │            │
   ▼           ▼            ▼
[PostgreSQL] [Redis Stack] [Gemini API]
 사용자·문서   벡터 검색·     임베딩·답변
 ·퀴즈 데이터  리프레시 토큰   생성
```

### RAG 흐름

```
자료 업로드 → 청킹 → 임베딩(Gemini) → Redis 벡터 스토어 저장
                                              │
질문 → 질문 임베딩 → 유사 청크 검색 ◀──────────┘
     → 질문 + 검색된 청크 결합 → LLM 답변(자료 근거) → SSE 스트리밍
```

---

## 🚀 실행 방법

### 사전 요구사항
- JDK 21
- Node.js (Vite 8 / React 19 호환 버전)
- Docker / Docker Compose
- Gemini API 키 ([Google AI Studio](https://aistudio.google.com/)에서 발급)

### 1) 환경변수 설정

루트의 `.env.example`를 복사해 `.env`를 만들고 값을 채웁니다. (`.env`는 커밋하지 않습니다.)

```bash
cp .env.example .env
```

필요한 키:

| 키 | 설명 |
|----|------|
| `POSTGRES_USER` | PostgreSQL 사용자명 |
| `POSTGRES_PASSWORD` | PostgreSQL 비밀번호 |
| `JWT_SECRET` | JWT 서명 시크릿 |
| `GEMINI_API_KEY` | Gemini API 키 (채팅·임베딩 공용) |

### 2) 인프라 실행 (PostgreSQL + Redis)

```bash
docker compose up -d
```

### 3) 백엔드 실행

```bash
cd backend
./gradlew bootRun        # Windows: .\gradlew.bat bootRun
```

- 서버: `http://localhost:8080`
- API 문서(Swagger UI): `http://localhost:8080/swagger-ui.html`

### 4) 프론트엔드 실행

```bash
cd frontend
npm install
npm run dev
```

- 개발 서버: Vite가 출력하는 주소(기본 `http://localhost:5173`)

---

## 🔧 주요 기술적 결정

개발 중 마주친 문제와 그 해결 과정은 **[docs/DECISIONS.md](docs/DECISIONS.md)** 에 정리되어 있습니다. 대표 항목 몇 가지:

- **RAG 임베딩 차원 불일치** — 벡터 검색이 0건을 반환하던 문제와 차원 강제 주입 우회
- **SSE 스트리밍 + Spring Security 비동기 처리 충돌**
- **스트리밍이 한 번에 표시되던 문제** — 계층별 추적으로 원인 규명

그 외에 인증(401)/인가(403) 의미 구분, `-parameters` 컴파일 플래그, 외부 LLM 쿼터 한계 대응 등이 문서에 함께 담겨 있습니다.

---

## 🗂 설계 문서

| 문서 | 내용 |
|------|------|
| [요구사항정의서](docs/요구사항정의서.md) | 기능·비기능 요구사항, 개발 우선순위 |
| [ERD](docs/ERD.md) | 데이터 모델링 |
| [API명세서](docs/API명세서.md) | REST API 규약 |
| [DECISIONS](docs/DECISIONS.md) | 기술 결정·트러블슈팅 기록 |

---

## 🔭 향후 개선

- 대화 기록 저장 및 이어가기
- 시맨틱 캐싱 / 챗봇 요청 rate limiting
- 소셜 로그인(구글·카카오)
- 배포 환경 구성
