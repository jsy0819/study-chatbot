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
      <div className="min-h-screen bg-surface flex items-center justify-center">
        <p className="text-red-600 text-sm">{error}</p>
      </div>
    );
  }

  if (!user) {
    return (
      <div className="min-h-screen bg-surface flex items-center justify-center">
        <div className="w-6 h-6 border-2 border-violet-200 border-t-violet-600 rounded-full animate-spin" />
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-surface">
      {/* 헤더 */}
      <header className="bg-white border-b border-violet-100">
        <div className="max-w-3xl mx-auto px-4 py-4 flex items-center justify-between">
          <span className="font-semibold text-gray-900">스터디 챗봇</span>
          <button onClick={handleLogout} className="nav-link">
            로그아웃
          </button>
        </div>
      </header>

      {/* 본문 */}
      <main className="max-w-3xl mx-auto px-4 py-12">
        <div className="card p-8">
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

          <div className="mt-8 pt-6 border-t border-violet-50 flex gap-3">
            <Link to="/chat" className="btn-primary">
              채팅 시작
            </Link>
            <Link to="/documents" className="btn-secondary">
              내 자료 관리
            </Link>
          </div>
        </div>
      </main>
    </div>
  );
}
