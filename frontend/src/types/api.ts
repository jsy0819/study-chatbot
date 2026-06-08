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
