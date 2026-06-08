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
