import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { ApiResponse, TokenRefreshResponse } from '../types/api';

// _retry 플래그를 config에 붙이기 위한 확장 타입
interface RetryConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

// 동시에 여러 요청이 401을 받아도 refresh 호출을 한 번만 하기 위한 공유 Promise
let refreshPromise: Promise<string> | null = null;

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  headers: { 'Content-Type': 'application/json' },
});

// 요청 인터셉터: JWT 주입 + FormData일 때 Content-Type 삭제(브라우저가 boundary 포함 헤더 자동 설정)
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('accessToken');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  if (config.data instanceof FormData) {
    delete config.headers['Content-Type'];
  }
  return config;
});

function redirectToLogin(): void {
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  window.location.href = '/login';
}

// apiClient 인터셉터를 타지 않도록 bare axios로 직접 호출
async function refreshAccessToken(): Promise<string> {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) throw new Error('No refresh token');

  const { data: res } = await axios.post<ApiResponse<TokenRefreshResponse>>(
    `${import.meta.env.VITE_API_BASE_URL}/api/auth/refresh`,
    { refreshToken },
  );
  const newToken = res.data.accessToken;
  localStorage.setItem('accessToken', newToken);
  return newToken;
}

// 응답 인터셉터: 액세스 토큰 만료(401) 시 자동 재발급 후 원래 요청 재시도
apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const config = error.config as RetryConfig | undefined;

    // config 없는 네트워크 오류, 이미 재시도한 요청, 인증 엔드포인트 자체는 제외
    if (
      error.response?.status === 401 &&
      config &&
      !config._retry &&
      !config.url?.includes('/api/auth/')
    ) {
      config._retry = true;

      try {
        // 진행 중인 refresh가 있으면 공유해 중복 호출 방지
        if (!refreshPromise) {
          refreshPromise = refreshAccessToken().finally(() => {
            refreshPromise = null;
          });
        }
        const newToken = await refreshPromise;
        config.headers.Authorization = `Bearer ${newToken}`;
        return apiClient(config);
      } catch {
        redirectToLogin();
        return Promise.reject(error);
      }
    }

    return Promise.reject(error);
  },
);

export default apiClient;
