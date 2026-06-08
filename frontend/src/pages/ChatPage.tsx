import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import apiClient from '../api/client';
import type { ApiResponse, ChatRequest, ChatResponse, DocumentResponse } from '../types/api';

interface ChatMessage {
  role: 'user' | 'ai';
  content: string;
}

export default function ChatPage() {
  const navigate = useNavigate();

  const [documents, setDocuments] = useState<DocumentResponse[]>([]);
  const [selectedDocId, setSelectedDocId] = useState<number | null>(null);
  const [loadingDocs, setLoadingDocs] = useState(true);

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');

  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!localStorage.getItem('accessToken')) {
      navigate('/login');
      return;
    }
    fetchDocuments();
  }, [navigate]);

  // 새 메시지나 로딩 상태가 바뀔 때 스크롤을 맨 아래로 이동
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending]);

  const fetchDocuments = async () => {
    setLoadingDocs(true);
    try {
      const { data: res } = await apiClient.get<ApiResponse<DocumentResponse[]>>('/api/documents');
      setDocuments(res.data.filter((d) => d.status === 'DONE'));
    } catch {
      // 빈 드롭다운으로 표시 — 사용자가 목록에서 상황 파악 가능
    } finally {
      setLoadingDocs(false);
    }
  };

  const handleDocChange = (docId: number | null) => {
    setSelectedDocId(docId);
    // 자료를 바꾸면 이전 대화와 섞이지 않도록 초기화
    setMessages([]);
    setError('');
  };

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedDocId || !input.trim() || sending) return;

    const userMessage = input.trim();
    setInput('');
    setError('');
    setMessages((prev) => [...prev, { role: 'user', content: userMessage }]);
    setSending(true);

    try {
      const body: ChatRequest = { documentId: selectedDocId, message: userMessage };
      const { data: res } = await apiClient.post<ApiResponse<ChatResponse>>('/api/chat', body);
      setMessages((prev) => [...prev, { role: 'ai', content: res.data.answer }]);
    } catch {
      setError('답변을 가져오지 못했습니다. 자료 처리 상태를 확인하거나 다시 시도해 주세요.');
    } finally {
      setSending(false);
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    navigate('/login');
  };

  return (
    <div className="h-screen flex flex-col bg-gray-50">
      {/* 헤더 */}
      <header className="flex-shrink-0 bg-white border-b border-gray-200">
        <div className="max-w-3xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link to="/" className="font-semibold text-gray-900 hover:text-gray-600 transition-colors">
              스터디 챗봇
            </Link>
            <span className="text-gray-200">|</span>
            <span className="text-sm text-gray-500 font-medium">채팅</span>
          </div>
          <div className="flex items-center gap-4">
            <Link to="/documents" className="text-sm text-gray-500 hover:text-gray-900 transition-colors">
              내 자료
            </Link>
            <button
              onClick={handleLogout}
              className="text-sm text-gray-500 hover:text-gray-900 transition-colors"
            >
              로그아웃
            </button>
          </div>
        </div>
      </header>

      {/* 자료 선택 바 */}
      <div className="flex-shrink-0 bg-white border-b border-gray-100">
        <div className="max-w-3xl mx-auto px-4 py-3">
          {loadingDocs ? (
            <div className="flex items-center gap-2 text-sm text-gray-400">
              <div className="w-3.5 h-3.5 border border-gray-300 border-t-gray-600 rounded-full animate-spin" />
              자료 불러오는 중...
            </div>
          ) : documents.length === 0 ? (
            <p className="text-sm text-gray-400">
              채팅 가능한 자료가 없습니다.{' '}
              <Link to="/documents" className="text-gray-900 underline underline-offset-2">
                자료 추가하기
              </Link>
            </p>
          ) : (
            <div className="flex items-center gap-3">
              <label htmlFor="doc-select" className="text-sm text-gray-500 flex-shrink-0">
                질문할 자료
              </label>
              <select
                id="doc-select"
                value={selectedDocId ?? ''}
                onChange={(e) => handleDocChange(e.target.value ? Number(e.target.value) : null)}
                className="flex-1 text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white focus:outline-none focus:ring-2 focus:ring-gray-900 focus:border-transparent"
              >
                <option value="">자료를 선택하세요</option>
                {documents.map((doc) => (
                  <option key={doc.id} value={doc.id}>
                    {doc.title}
                  </option>
                ))}
              </select>
            </div>
          )}
        </div>
      </div>

      {/* 채팅 메시지 영역 */}
      <div className="flex-1 overflow-y-auto">
        <div className="max-w-3xl mx-auto px-4 py-6 space-y-4">
          {/* 자료 미선택 안내 */}
          {!selectedDocId && messages.length === 0 && !loadingDocs && (
            <div className="flex items-center justify-center h-48">
              <p className="text-sm text-gray-400 text-center">
                {documents.length === 0
                  ? '먼저 자료를 업로드하고 처리가 완료되면 질문할 수 있습니다.'
                  : '위에서 자료를 선택하면 질문을 시작할 수 있습니다.'}
              </p>
            </div>
          )}

          {/* 대화 메시지 */}
          {messages.map((msg, i) => (
            <div key={i} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
              <div
                className={`max-w-[75%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed whitespace-pre-wrap ${
                  msg.role === 'user'
                    ? 'bg-gray-900 text-white rounded-br-sm'
                    : 'bg-white border border-gray-200 text-gray-800 rounded-bl-sm'
                }`}
              >
                {msg.content}
              </div>
            </div>
          ))}

          {/* 답변 생성 중 — 점 바운스 애니메이션 */}
          {sending && (
            <div className="flex justify-start">
              <div className="bg-white border border-gray-200 rounded-2xl rounded-bl-sm px-4 py-3.5">
                <div className="flex gap-1 items-center">
                  <span className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce [animation-delay:0ms]" />
                  <span className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce [animation-delay:150ms]" />
                  <span className="w-1.5 h-1.5 bg-gray-400 rounded-full animate-bounce [animation-delay:300ms]" />
                </div>
              </div>
            </div>
          )}

          {/* 에러 메시지 — AI 말풍선 스타일로 인라인 표시 */}
          {error && (
            <div className="flex justify-start">
              <div className="max-w-[75%] bg-red-50 border border-red-200 text-red-600 rounded-2xl rounded-bl-sm px-4 py-2.5 text-sm leading-relaxed">
                {error}
              </div>
            </div>
          )}

          <div ref={bottomRef} />
        </div>
      </div>

      {/* 입력 영역 */}
      <div className="flex-shrink-0 bg-white border-t border-gray-200">
        <div className="max-w-3xl mx-auto px-4 py-3">
          <form onSubmit={handleSend} className="flex gap-2">
            <input
              type="text"
              placeholder={selectedDocId ? '질문을 입력하세요' : '자료를 먼저 선택하세요'}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              disabled={!selectedDocId || sending}
              className="flex-1 border border-gray-200 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900 focus:border-transparent disabled:bg-gray-50 disabled:text-gray-400"
            />
            <button
              type="submit"
              disabled={!selectedDocId || !input.trim() || sending}
              className="flex-shrink-0 bg-gray-900 text-white text-sm rounded-xl px-5 py-2.5 hover:bg-gray-700 transition-colors disabled:opacity-40"
            >
              전송
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
