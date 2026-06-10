// 백엔드 ApiResponse<T> 래퍼 구조
export interface ApiResponse<T> {
  data: T;
}

// POST /api/auth/login 응답
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
}

// GET /api/users/me 응답
export interface UserMeResponse {
  id: number;
  email: string;
  name: string;
}

// GET /api/documents, POST /api/documents, POST /api/documents/pdf 응답
export interface DocumentResponse {
  id: number;
  title: string;
  status: 'PROCESSING' | 'DONE' | 'FAILED';
  createdAt: string;
}

// POST /api/chat 요청
export interface ChatRequest {
  documentId: number;
  message: string;
}

// POST /api/chat 응답
export interface ChatResponse {
  answer: string;
}

// POST /api/auth/refresh 응답
export interface TokenRefreshResponse {
  accessToken: string;
  tokenType: string;
}

// ── 퀴즈 ──────────────────────────────────────────────────────

// 허용되는 문제 수 (백엔드 검증값과 일치)
export type QuestionCount = 3 | 5 | 10;

// POST /api/quiz/generate 요청
export interface QuizGenerationRequest {
  documentId: number;
  questionCount: QuestionCount;
}

// 생성 응답의 문제 — 정답(answerIndex)·해설(explanation) 없음
export interface QuizQuestion {
  id: number;
  questionOrder: number;
  question: string;
  choices: string[];
}

// POST /api/quiz/generate 응답
export interface QuizSessionCreateResponse {
  sessionId: number;
  questions: QuizQuestion[];
}

// POST /api/quiz/{sessionId}/submit 요청의 개별 답안
export interface QuizAnswer {
  questionId: number;
  selectedIndex: number;
}

// POST /api/quiz/{sessionId}/submit 요청
export interface QuizSubmitRequest {
  answers: QuizAnswer[];
}

// 제출 응답의 문제 — 정답·내 답·정답 여부·해설 공개
export interface QuizResultQuestion {
  questionId: number;
  questionOrder: number;
  question: string;
  choices: string[];
  answerIndex: number;
  userAnswerIndex: number;
  correct: boolean;
  explanation: string;
}

// POST /api/quiz/{sessionId}/submit 응답
// 백엔드 QuizSubmitResponse DTO와 필드명 일치 (score / totalCount / results).
// documentTitle·sessionId는 응답에 포함되지 않으므로 화면에서 별도로 보강한다.
export interface QuizSubmitResponse {
  score: number;
  totalCount: number;
  results: QuizResultQuestion[];
}

// GET /api/quiz/sessions 응답의 세션 요약 (백엔드 QuizSessionSummary와 필드명 일치)
// 미제출 세션이면 score·submittedAt이 null로 내려온다.
export interface QuizSessionSummary {
  sessionId: number;
  documentTitle: string;
  questionCount: number;
  score: number | null;
  createdAt: string;
  submittedAt: string | null;
}

// GET /api/quiz/sessions/{sessionId} 응답 (백엔드 QuizSessionDetailResponse와 필드명 일치)
// 문제 항목은 백엔드가 제출 응답과 같은 QuizResultItem을 쓰므로 QuizResultQuestion을 재사용한다.
// (제출된 세션 상세만 화면에서 열기 때문에 answerIndex·correct 등은 항상 채워져 있다.)
export interface QuizSessionDetailResponse {
  sessionId: number;
  documentTitle: string;
  questionCount: number;
  score: number | null;
  createdAt: string;
  submittedAt: string | null;
  questions: QuizResultQuestion[];
}
