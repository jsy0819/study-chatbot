import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import apiClient from '../api/client';
import type { ApiResponse, LoginResponse } from '../types/api';

export default function LoginPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState({ email: '', password: '' });
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const { data: res } = await apiClient.post<ApiResponse<LoginResponse>>(
        '/api/auth/login',
        form
      );
      localStorage.setItem('accessToken', res.data.accessToken);
      localStorage.setItem('refreshToken', res.data.refreshToken);
      navigate('/');
    } catch {
      setError('이메일 또는 비밀번호가 올바르지 않습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface flex items-center justify-center px-4 relative overflow-hidden">
      {/* 배경 장식: 부드러운 바이올렛 글로우 */}
      <div className="absolute -top-32 -right-32 w-96 h-96 rounded-full bg-violet-100/50 blur-3xl pointer-events-none" />
      <div className="absolute -bottom-32 -left-32 w-80 h-80 rounded-full bg-violet-50/70 blur-3xl pointer-events-none" />

      <div className="auth-card">
        {/* 브랜드 아이콘 */}
        <div className="flex justify-center mb-6">
          <div className="w-12 h-12 rounded-2xl bg-violet-600/10 flex items-center justify-center">
            <svg
              className="w-6 h-6 text-violet-600"
              xmlns="http://www.w3.org/2000/svg"
              fill="none"
              viewBox="0 0 24 24"
              strokeWidth={1.8}
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                d="M12 6.042A8.967 8.967 0 0 0 6 3.75c-1.052 0-2.062.18-3 .512v14.25A8.987 8.987 0 0 1 6 18c2.305 0 4.408.867 6 2.292m0-14.25a8.966 8.966 0 0 1 6-2.292c1.052 0 2.062.18 3 .512v14.25A8.987 8.987 0 0 0 18 18a8.967 8.967 0 0 0-6 2.292m0-14.25v14.25"
              />
            </svg>
          </div>
        </div>

        <h1 className="text-2xl font-semibold text-gray-900 text-center mb-1">로그인</h1>
        <p className="text-sm text-gray-500 text-center mb-7">계속하려면 로그인하세요</p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">이메일</label>
            <input
              type="email"
              required
              value={form.email}
              onChange={(e) => setForm({ ...form, email: e.target.value })}
              className="input-field"
              placeholder="you@example.com"
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1.5">비밀번호</label>
            <input
              type="password"
              required
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              className="input-field"
              placeholder="비밀번호"
            />
          </div>

          {error && (
            <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2.5">
              <svg className="w-4 h-4 flex-shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z" />
              </svg>
              <span>{error}</span>
            </div>
          )}

          <button
            type="submit"
            disabled={loading}
            className="btn-primary w-full mt-2"
          >
            {loading ? '처리 중...' : '로그인'}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-gray-500">
          계정이 없으신가요?{' '}
          <Link
            to="/signup"
            className="font-semibold text-violet-600 hover:text-violet-700 transition-colors"
          >
            회원가입
          </Link>
        </p>
      </div>
    </div>
  );
}
