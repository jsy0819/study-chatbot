# 기술 결정 기록 (DECISIONS)

이 문서는 "학습자료 기반 스터디 챗봇"을 만들며 마주친 주요 기술적 문제와 그 해결 과정을 정리한 것이다.
단순히 "무엇을 했는지"가 아니라 **왜 그렇게 결정했는지**에 초점을 맞췄다.

프로젝트 개요: 학습 자료(텍스트/PDF)를 업로드하면, 그 자료를 근거로 질문에 답하는 RAG 챗봇.
스택은 Spring Boot 3.5 / Java 21 / PostgreSQL / Redis(redis-stack) / Spring AI / Google Gemini / React + Vite + TypeScript.

---

## 1. RAG 벡터 검색이 0건을 반환한 문제 — 임베딩 차원 불일치

**문제**
자료를 업로드하면 임베딩이 정상적으로 저장되는 것처럼 보였는데, 막상 질문하면 벡터 검색 결과가 항상 0건이었다.
검색이 안 되니 RAG의 핵심인 "자료 기반 답변"이 전혀 동작하지 않았다.

**원인 추적**
Redis(redis-stack)의 `FT.INFO` 명령으로 인덱스 상태를 직접 들여다봤다. 그 결과 인덱스의 벡터 차원이 `dim: 768`로 잡혀 있는데,
실제 임베딩 모델(`gemini-embedding-001`)이 만들어내는 벡터는 3072차원이었다. 저장 시점에 "Expected 768, got 3072" 에러가 나며 색인이 실패하고 있었다.

근본 원인은 Spring AI의 `GoogleGenAiTextEmbeddingModel`이 `gemini-embedding-001`의 차원을 768로 잘못 보고하는 점이었다.
`RedisVectorStore`는 인덱스를 만들 때 `embeddingModel.dimensions()` 값만 사용하고, 차원을 직접 지정하는 설정이 없었다.

**해결**
`dimensions()`가 설정값(3072)을 반환하도록 위임하는 래퍼(`FixedDimensionEmbeddingModel`)를 만들어 우회했다.
차원 값은 매직 넘버로 코드에 박지 않고 `application.properties`(`app.embedding.dimensions=3072`)로 분리하고,
"임베딩 모델을 바꾸면 이 값도 함께 바꿔야 한다"는 주석을 남겼다. 모델명과 차원이 같은 파일에 나란히 있어 변경 시 실수를 줄일 수 있다.

차원을 바꾼 뒤에도 기존 768 인덱스가 Redis 볼륨에 남아 있어 계속 실패했는데, 볼륨까지 비우고(`docker compose down -v`) 재생성하니 정상화됐다.
"컨테이너 삭제 ≠ 데이터 삭제"라는 점을 체감한 지점이다.

**배운 점**
라이브러리가 항상 옳지는 않다. 증상만 보고 추측하는 대신 `FT.INFO`로 인덱스 내부를 직접 확인해 "차원 불일치"라는 진짜 원인에 도달할 수 있었다.

---

## 2. Spring AI와 DevTools의 클래스로더 충돌

**문제**
벡터 스토어 설정을 추가한 뒤 애플리케이션이 `NoClassDefFoundError`로 시작조차 못 했다.

**원인**
Spring Boot DevTools는 빠른 재시작을 위해 별도의 RestartClassLoader를 쓰는데, 이것이 Spring AI의 클래스 로딩과 충돌했다.

**해결**
편의 기능인 DevTools를 제거했다. 자동 재시작을 잃는 대신 안정성을 택한 것이다.
이후로는 서버를 수동 재시작해야 했지만, 핵심 기능의 안정성이 더 중요하다고 판단했다.

**배운 점**
개발 편의 도구가 프레임워크와 충돌할 수 있다. "편의 기능을 빼는 것"도 합리적인 결정이 될 수 있다.

---

## 3. 인증 실패(401)와 인가 실패(403)의 의미 구분

**문제**
프론트엔드에 토큰 자동 갱신(리프레시) 로직을 붙였는데, 토큰이 만료되면 갱신되지 않고 그냥 로그아웃됐다.

**원인**
프론트 인터셉터는 401(Unauthorized)일 때만 토큰을 재발급하도록 했는데, 백엔드는 토큰 만료 시 403(Forbidden)을 반환하고 있었다.
Spring Security 6에서 httpBasic/formLogin을 모두 끄면, 인증 실패 시 기본 EntryPoint가 `Http403ForbiddenEntryPoint`로 동작하기 때문이다.

**고민한 선택지**
"프론트가 403도 토큰 만료로 간주하게" 하는 빠른 방법도 있었다. 하지만 이 코드베이스에서는 위험했다.
이미 403은 "남의 문서에 접근" 같은 **진짜 권한 거부**에 쓰이고 있었기 때문이다(ChatService, DocumentService).
만약 403을 만료로 처리하면, 정당한 권한 거부 상황에서 불필요한 재발급 → 재거부 → 강제 로그아웃이라는 잘못된 동작이 발생한다.

**해결**
프론트가 아니라 백엔드에서 HTTP 의미를 바로잡았다.
- 401 = 인증 안 됨(토큰 없음/만료/무효) → 프론트가 재발급 시도
- 403 = 인증은 됐으나 권한 없음 → 그대로 에러

커스텀 `JwtAuthenticationEntryPoint`를 만들어 미인증 요청에 401을 반환하게 하고 SecurityConfig에 연결했다.
이렇게 하니 프론트의 기존 401 핸들러가 손대지 않고도 정확히 동작했다.

**배운 점**
증상에 맞춰 빠르게 막는 대신, HTTP 상태 코드의 본래 의미(401 vs 403)에 맞게 설계하는 것이 부작용 없는 정답이었다.

---

## 4. @PathVariable 이름 손실 — 컴파일러 -parameters 플래그

**문제**
`DELETE /api/documents/{id}` 호출 시 500 에러가 났다. 목록 조회나 업로드는 정상이었다.

**원인**
에러 메시지가 직접 알려줬다: "parameter name information not available ... Ensure that the compiler uses the '-parameters' flag."
Java는 컴파일 시 `-parameters` 플래그가 있어야 메서드 파라미터 이름을 `.class`에 보존한다. 이 플래그가 없으면 `@PathVariable Long id`의 이름 `id`가 사라져,
Spring이 URL의 `{id}`를 어느 파라미터에 바인딩할지 알 수 없게 된다. 경로 변수를 쓰는 API는 삭제뿐이라 거기서만 드러났다.

**해결**
- 근본 해결: `build.gradle`에 `-parameters` 컴파일 옵션을 추가해 모든 파라미터 이름이 보존되게 했다.
- 이중 안전장치: `@PathVariable("id")`처럼 이름을 명시했다. 플래그가 없어도 동작하도록.

**배운 점**
같은 패턴의 API라도 경로 변수 유무에 따라 특정 케이스에서만 문제가 드러날 수 있다. 근본 원인(빌드 설정)을 고치되, 방어적 보완을 함께 두는 습관이 안전하다.

---

## 5. SSE 스트리밍과 Spring Security 비동기 처리 충돌

**문제**
RAG 답변을 ChatGPT처럼 실시간 스트리밍(SSE)하려 했는데, 인증과 RAG 검색까지는 성공("검색된 청크 수=1" 로그)한 뒤
응답 도중 `AuthorizationDeniedException: Access Denied`가 나며 스트림이 끊겼다. 프론트엔 `ERR_INCOMPLETE_CHUNKED_ENCODING`으로 보였다.

**원인**
`SseEmitter`를 반환하면 요청이 비동기(async) 모드로 전환된다. 스트림이 끝나면 서블릿 컨테이너가 `DispatcherType.ASYNC`로 필터 체인을 **다시 한 번** 태운다.
이 재진입 스레드에는 SecurityContext(인증 정보)가 없어, Spring Security의 `AuthorizationFilter`가 `anyRequest().authenticated()`를 통과 못 시키고 Access Denied를 던진다.
이미 응답이 시작(committed)된 뒤라 에러 페이지도 만들 수 없었다("response already committed" 로그가 이를 뒷받침).

**검토하고 버린 방법**
- `SecurityContextHolder`를 INHERITABLETHREADLOCAL로 바꾸기: reactor 스케줄러/서블릿 async 스레드는 풀에서 빌려오는 거라 부모-자식 상속이 안 돼 불안정.
- 구독 스레드에 컨텍스트 수동 전파: 실제로 막히는 건 ASYNC 디스패치(컨테이너) 스레드라 효과 없음.

**해결**
`dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()`로 ASYNC 디스패치만 인가 대상에서 제외했다.
- 최초 요청(REQUEST)은 이미 정상 인증을 거쳤다.
- ASYNC 재진입은 컨테이너 내부에서만 발생하므로 외부 위조가 불가능해 재인가가 불필요하다.
- 엔드포인트 자체를 열어버리는 것이 아니라 **ASYNC 디스패치 타입만** 여는 것이라, 외부 요청(REQUEST)은 그대로 인증을 거친다.

**배운 점**
SSE는 단순한 응답이 아니라 서블릿의 비동기 처리 위에서 동작한다. 프레임워크가 요청을 어떻게 처리하는지(디스패치 타입의 재진입)를 이해해야
보안을 깨지 않으면서 문제를 풀 수 있었다.

---

## 6. 외부 LLM 무료 쿼터 한계와 우아한 에러 처리

**문제**
개발 중 Gemini 무료 등급의 요청 한도(분당·일당)를 초과해 LLM 호출이 429로 실패했다.
스트리밍 중 이 에러가 나면 Flux가 그대로 종료되며 스트림이 깨졌다.

**해결**
스트리밍 Flux에 `onErrorResume`을 붙여, LLM 호출 실패를 사용자용 안내 메시지로 치환하고 스트림을 정상 종료시켰다.
이제 쿼터 초과 같은 상황에서도 화면이 깨지는 대신 "잠시 후 다시 시도해주세요" 안내가 표시된다.

**배운 점**
외부 서비스는 언제든 실패할 수 있다(한도, 장애 등). 그 실패를 사용자에게 어떻게 보여줄지까지가 기능의 일부다.

---

## 설계 전반에서 지킨 원칙

- **추측보다 근거**: 에러 메시지·라이브러리 소스·인덱스 상태(`FT.INFO`)·네트워크 탭 등 실제 증거로 원인을 좁혔다.
- **빠른 우회보다 올바른 의미**: 401/403 구분처럼, 증상만 막는 대신 의미에 맞게 설계해 부작용을 피했다.
- **매직 넘버 분리**: 임베딩 차원 같은 값은 설정으로 빼고 의도를 주석으로 남겼다.
- **기존 동작 보존**: 새 기능(스트리밍)을 추가할 때 기존 API는 그대로 두어 롤백·비교가 가능하게 했다.
