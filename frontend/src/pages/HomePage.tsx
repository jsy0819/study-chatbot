import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import apiClient from '../api/client';
import type { ApiResponse, UserMeResponse } from '../types/api';

export default function HomePage() {
  const navigate = useNavigate();
  const [user, setUser] = useState<UserMeResponse | null>(null);
  const [error, setError] = useState('');

  useEffect(() => {
    const token = localStorage.getItem('accessToken');
    if (!token) {
      navigate('/login');
      return;
    }

    apiClient
      .get<ApiResponse<UserMeResponse>>('/api/users/me')
      .then(({ data: res }) => setUser(res.data))
      .catch(() => {
        // 토큰 만료 또는 인증 실패 → 로그아웃 처리
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        navigate('/login');
        setError('세션이 만료되었습니다. 다시 로그인해 주세요.');
      });
  }, [navigate]);

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    navigate('/login');
  };

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <p className="text-red-600 text-sm">{error}</p>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="w-6 h-6 border-2 border-gray-300 border-t-gray-900 rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* 헤더 */}
      <header className="bg-white border-b border-gray-200">
        <div className="max-w-3xl mx-auto px-4 py-4 flex items-center justify-between">
          <span className="font-semibold text-gray-900">스터디 챗봇</span>
          <button
            onClick={handleLogout}
            className="text-sm text-gray-500 hover:text-gray-900 transition-colors"
          >
            로그아웃
          </button>
        </div>
      </header>

      {/* 본문 */}
      <main className="max-w-3xl mx-auto px-4 py-12">
        <div className="bg-white rounded-2xl border border-gray-200 p-8">
          <p className="text-sm text-gray-500 mb-1">환영합니다 👋</p>
          <h1 className="text-2xl font-semibold text-gray-900 mb-6">{user.name}님</h1>

          <dl className="space-y-3">
            <div className="flex items-center gap-4 text-sm">
              <dt className="w-16 text-gray-500">이메일</dt>
              <dd className="text-gray-900">{user.email}</dd>
            </div>
            <div className="flex items-center gap-4 text-sm">
              <dt className="w-16 text-gray-500">이름</dt>
              <dd className="text-gray-900">{user.name}</dd>
            </div>
            <div className="flex items-center gap-4 text-sm">
              <dt className="w-16 text-gray-500">ID</dt>
              <dd className="text-gray-400">{user.id}</dd>
            </div>
          </dl>

          <div className="mt-8 pt-6 border-t border-gray-100 flex gap-3">
            <Link
              to="/chat"
              className="inline-flex items-center gap-2 bg-gray-900 text-white text-sm rounded-lg px-4 py-2 hover:bg-gray-700 transition-colors"
            >
              채팅 시작
            </Link>
            <Link
              to="/documents"
              className="inline-flex items-center gap-2 border border-gray-200 text-gray-700 text-sm rounded-lg px-4 py-2 hover:bg-gray-50 transition-colors"
            >
              내 자료 관리
            </Link>
          </div>
        </div>
      </main>
    </div>
  );
}
