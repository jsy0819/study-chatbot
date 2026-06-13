import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import apiClient, { redirectToLogin, refreshAccessToken } from '../api/client';
import type {
  ApiResponse,
  ChatRequest,
  ChatSessionDetailResponse,
  ChatSessionSummary,
  DocumentResponse,
} from '../types/api';

interface ChatMessage {
  role: 'user' | 'ai';
  content: string;
}

function streamErrorMessage(status: number): string {
  switch (status) {
    case 403: return '이 자료에 대한 접근 권한이 없습니다.';
    case 404: return '자료를 찾을 수 없습니다. 삭제되었을 수 있습니다.';
    default:  return '답변을 가져오지 못했습니다. 다시 시도해 주세요.';
  }
}

// 사이드바 항목용 짧은 날짜 표기 (LocalDateTime ISO, 타임존 없음)
function formatSessionDate(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric' });
}

export default function ChatPage() {
  const navigate = useNavigate();

  // ── 새 대화용 자료 선택 ──
  const [documents, setDocuments] = useState<DocumentResponse[]>([]);
  const [selectedDocId, setSelectedDocId] = useState<number | null>(null);
  const [loadingDocs, setLoadingDocs] = useState(true);

  // ── 현재 대화 ──
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [sending, setSending] = useState(false);
  const [error, setError] = useState('');
  // null: 스트리밍 없음 / string: 스트리밍 중 (빈 문자열이면 첫 토큰 대기)
  const [streamingContent, setStreamingContent] = useState<string | null>(null);

  // ── 세션 상태 ──
  // null이면 새 대화(자료 선택부터). 값이 있으면 그 세션에 이어쓰기.
  const [currentSessionId, setCurrentSessionId] = useState<number | null>(null);
  // 세션에 고정된 자료 — 이어쓰기 요청 시 보낼 documentId와 표시용 제목.
  // 상세 응답엔 documentId가 없어 제목으로 매칭해 복원하므로 못 찾으면 null(이어쓰기 불가).
  const [activeDocId, setActiveDocId] = useState<number | null>(null);
  const [activeDocTitle, setActiveDocTitle] = useState('');

  // ── 사이드바(지난 대화 목록) ──
  const [sessions, setSessions] = useState<ChatSessionSummary[]>([]);
  const [loadingSessions, setLoadingSessions] = useState(true);
  const [sessionsError, setSessionsError] = useState('');
  const [openingSessionId, setOpeningSessionId] = useState<number | null>(null);
  const [deletingSessionId, setDeletingSessionId] = useState<number | null>(null);
  // 모바일에서만 의미 있는 드로어 열림 상태 (데스크톱은 항상 표시)
  const [sidebarOpen, setSidebarOpen] = useState(false);

  const bottomRef = useRef<HTMLDivElement>(null);

  const isSessionMode = currentSessionId !== null;
  // 새 대화면 드롭다운 선택, 세션 모드면 세션에 고정된 자료가 있어야 전송 가능
  const canSend = isSessionMode ? activeDocId !== null : selectedDocId !== null;

  useEffect(() => {
    if (!localStorage.getItem('accessToken')) {
      navigate('/login');
      return;
    }
    fetchDocuments();
    fetchSessions();
  }, [navigate]);

  // 새 메시지·스트리밍 토큰 도착 시 스크롤을 맨 아래로 이동
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, sending, streamingContent]);

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

  // 지난 대화 목록을 불러온다 (최신순은 백엔드가 보장).
  const fetchSessions = async () => {
    setLoadingSessions(true);
    setSessionsError('');
    try {
      const { data: res } = await apiClient.get<ApiResponse<ChatSessionSummary[]>>('/api/chat/sessions');
      setSessions(res.data);
    } catch {
      setSessionsError('대화 목록을 불러오지 못했습니다.');
    } finally {
      setLoadingSessions(false);
    }
  };

  const handleDocChange = (docId: number | null) => {
    setSelectedDocId(docId);
    // 자료를 바꾸면 이전 대화와 섞이지 않도록 초기화
    setMessages([]);
    setError('');
  };

  // "새 대화" — 세션·메시지·선택을 모두 초기화해 자료 선택부터 다시 시작
  const handleNewChat = () => {
    setCurrentSessionId(null);
    setActiveDocId(null);
    setActiveDocTitle('');
    setSelectedDocId(null);
    setMessages([]);
    setInput('');
    setError('');
    setStreamingContent(null);
    setSidebarOpen(false);
  };

  // 지난 대화 클릭 → 상세 조회 후 메인에 표시하고 이어쓰기 가능 상태로 전환
  const handleOpenSession = async (sessionId: number) => {
    if (openingSessionId !== null || sending) return;
    setOpeningSessionId(sessionId);
    setError('');
    try {
      const { data: res } = await apiClient.get<ApiResponse<ChatSessionDetailResponse>>(
        `/api/chat/sessions/${sessionId}`,
      );
      const detail = res.data;
      const ordered = [...detail.messages].sort((a, b) => a.messageOrder - b.messageOrder);
      setMessages(ordered.map((m) => ({
        role: m.role === 'USER' ? 'user' : 'ai',
        content: m.content,
      })));
      setCurrentSessionId(detail.sessionId);
      setActiveDocTitle(detail.documentTitle);
      // 상세 응답엔 documentId가 없어 제목으로 매칭해 복원한다(@NotNull 충족용).
      // 못 찾으면(자료 삭제 등) null → 이어쓰기는 막고 기록만 보여준다.
      const matched = documents.find((d) => d.title === detail.documentTitle);
      setActiveDocId(matched ? matched.id : null);
      setSelectedDocId(null);
      setInput('');
      setSidebarOpen(false);
    } catch {
      setError('대화를 불러오지 못했습니다. 다시 시도해 주세요.');
    } finally {
      setOpeningSessionId(null);
    }
  };

  // 세션 삭제 → 목록에서 제거. 보던 세션이면 새 대화 상태로 전환.
  const handleDeleteSession = async (sessionId: number, e: React.MouseEvent) => {
    e.stopPropagation(); // 항목 선택(열기)으로 이어지지 않게
    if (deletingSessionId !== null) return;
    setDeletingSessionId(sessionId);
    setSessionsError('');
    try {
      await apiClient.delete(`/api/chat/sessions/${sessionId}`);
      setSessions((prev) => prev.filter((s) => s.sessionId !== sessionId));
      if (currentSessionId === sessionId) {
        handleNewChat();
      }
    } catch {
      setSessionsError('대화를 삭제하지 못했습니다. 다시 시도해 주세요.');
    } finally {
      setDeletingSessionId(null);
    }
  };

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    // 새 대화면 드롭다운 선택, 이어쓰기면 세션에 고정된 자료의 id (둘 다 null 가드)
    const docId = isSessionMode ? activeDocId : selectedDocId;
    if (!docId || !input.trim() || sending) return;

    const userMessage = input.trim();
    const sessionIdAtSend = currentSessionId; // 전송 시점 세션 (null이면 새 세션 생성)
    setInput('');
    setError('');
    setMessages((prev) => [...prev, { role: 'user', content: userMessage }]);
    setSending(true);

    // fetch로 SSE 스트리밍 호출. axios 인터셉터를 거치지 않으므로 401 시 직접 refresh.
    const doStream = async (retried: boolean): Promise<void> => {
      const accessToken = localStorage.getItem('accessToken') ?? '';
      const body: ChatRequest = {
        documentId: docId,
        message: userMessage,
        // 이어쓰기면 세션 지정, 새 대화면 생략 → 백엔드가 새 세션 생성
        ...(sessionIdAtSend !== null && { sessionId: sessionIdAtSend }),
      };

      let response: Response;
      try {
        response = await fetch(`${import.meta.env.VITE_API_BASE_URL}/api/chat/stream`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${accessToken}`,
          },
          body: JSON.stringify(body),
        });
      } catch {
        setError('네트워크 오류가 발생했습니다. 인터넷 연결을 확인해 주세요.');
        setSending(false);
        return;
      }

      if (!response.ok) {
        if (response.status === 401 && !retried) {
          try {
            await refreshAccessToken();
            return doStream(true);
          } catch {
            redirectToLogin();
            return;
          }
        }
        setError(streamErrorMessage(response.status));
        setSending(false);
        return;
      }

      if (!response.body) {
        setError('스트림을 읽을 수 없습니다.');
        setSending(false);
        return;
      }

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let sseBuffer = '';        // 청크 경계에서 잘린 SSE 데이터 보존용
      let accumulated = '';      // 누적된 답변 토큰
      let receivedSessionId: number | null = null; // session 이벤트로 받은 세션 id
      setStreamingContent(''); // 이 시점부터 스트리밍 말풍선 표시

      // 하나의 SSE 이벤트 블록을 처리한다.
      // 첫 이벤트는 'event: session' + sessionId, 그 뒤는 이름 없는(기본) 이벤트로 답변 토큰이 온다.
      // session 이벤트만 분기해 id를 뽑고, 나머지는 기존과 동일하게 토큰으로 누적한다.
      const handleEvent = (block: string) => {
        let eventName = '';
        let data = '';
        for (const line of block.split('\n')) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            data += line.slice(5); // "data:" 접두사 제거 (공백·내용은 그대로 보존)
          }
        }
        if (eventName === 'session') {
          const id = Number(data.trim());
          if (!Number.isNaN(id)) receivedSessionId = id;
          return; // sessionId는 답변 토큰이 아니므로 누적하지 않는다
        }
        accumulated += data;
      };

      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          sseBuffer += decoder.decode(value, { stream: true });

          // SSE 이벤트는 '\n\n'으로 구분. 마지막 조각은 다음 청크와 이어질 수 있어 버퍼에 보존.
          const events = sseBuffer.split('\n\n');
          sseBuffer = events.pop() ?? '';

          for (const event of events) {
            if (event.trim()) handleEvent(event);
          }

          // 청크당 한 번만 상태를 갱신한다.
          // setTimeout(0)으로 현재 매크로태스크를 끊어야 브라우저가 React 렌더와
          // 페인트를 처리할 기회를 얻는다. reader.read()가 내부 버퍼의 데이터로
          // 연속으로 즉시 resolve되면 JS 콜 스택이 비지 않아 페인트가 지연되는데,
          // 이 yield가 그 구조를 막는다.
          setStreamingContent(accumulated);
          await new Promise<void>((resolve) => setTimeout(resolve, 0));
        }

        // 스트림 종료 후 버퍼에 남은 이벤트 처리
        if (sseBuffer.trim()) handleEvent(sseBuffer);

        setMessages((prev) => [...prev, { role: 'ai', content: accumulated }]);

        // 세션 확정 — 새로 생성된 세션이면 이어쓰기용 자료를 고정하고 사이드바 목록을 갱신한다.
        if (receivedSessionId !== null) {
          setCurrentSessionId(receivedSessionId);
          if (sessionIdAtSend === null) {
            setActiveDocId(docId);
            setActiveDocTitle(documents.find((d) => d.id === docId)?.title ?? '');
            fetchSessions();
          }
        }
      } catch {
        setError('스트리밍 중 연결이 끊겼습니다. 다시 시도해 주세요.');
      } finally {
        setStreamingContent(null);
        setSending(false);
      }
    };

    await doStream(false);
  };

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    navigate('/login');
  };

  return (
    <div className="h-screen flex flex-col bg-surface">
      {/* 헤더 — 다른 화면과 동일한 네비게이션. 모바일에선 사이드바 토글 버튼이 추가됨 */}
      <header className="flex-shrink-0 bg-white border-b border-violet-100">
        <div className="px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={() => setSidebarOpen((o) => !o)}
              className="md:hidden -ml-1 p-1.5 rounded-lg text-gray-500 hover:bg-violet-50 transition-colors"
              aria-label="대화 목록 열기"
            >
              <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5" />
              </svg>
            </button>
            <Link to="/" className="font-semibold text-gray-900 hover:text-violet-600 transition-colors">
              스터디 챗봇
            </Link>
            <span className="text-violet-100">|</span>
            <span className="text-sm text-gray-500 font-medium">채팅</span>
          </div>
          <div className="flex items-center gap-4">
            <Link to="/documents" className="nav-link">내 자료</Link>
            <Link to="/quiz" className="nav-link">퀴즈</Link>
            <button onClick={handleLogout} className="nav-link">로그아웃</button>
          </div>
        </div>
      </header>

      {/* 본문 — [사이드바 | 메인] */}
      <div className="flex-1 flex min-h-0 relative">
        {/* 모바일 드로어 백드롭 */}
        {sidebarOpen && (
          <div
            className="md:hidden fixed inset-0 bg-black/30 z-30"
            onClick={() => setSidebarOpen(false)}
          />
        )}

        {/* 사이드바 — 데스크톱은 고정 컬럼, 모바일은 왼쪽 슬라이드 드로어 */}
        <aside
          className={`fixed md:static inset-y-0 left-0 z-40 w-72 flex-shrink-0 bg-white border-r border-violet-100
                      flex flex-col transform transition-transform duration-200 md:transform-none
                      ${sidebarOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0'}`}
        >
          <div className="p-3 flex-shrink-0">
            <button
              type="button"
              onClick={handleNewChat}
              className="btn-primary w-full flex items-center justify-center gap-1.5"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" strokeWidth={2.2} stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
              </svg>
              새 대화
            </button>
          </div>

          <div className="flex-1 overflow-y-auto px-3 pb-3">
            {sessionsError && <SidebarError message={sessionsError} />}

            {loadingSessions ? (
              <div className="flex items-center gap-2 text-sm text-gray-400 px-1 py-2">
                <div className="w-3.5 h-3.5 border border-violet-200 border-t-violet-500 rounded-full animate-spin" />
                불러오는 중...
              </div>
            ) : sessions.length === 0 ? (
              <p className="text-sm text-gray-400 px-1 py-2">아직 지난 대화가 없습니다.</p>
            ) : (
              <ul className="space-y-1">
                {sessions.map((s) => (
                  <SessionItem
                    key={s.sessionId}
                    session={s}
                    active={currentSessionId === s.sessionId}
                    opening={openingSessionId === s.sessionId}
                    deleting={deletingSessionId === s.sessionId}
                    disabled={openingSessionId !== null}
                    onOpen={handleOpenSession}
                    onDelete={handleDeleteSession}
                  />
                ))}
              </ul>
            )}
          </div>
        </aside>

        {/* 메인 — 자료 바 + 메시지 + 입력 */}
        <div className="flex-1 flex flex-col min-w-0">
          {/* 자료 바 */}
          <div className="flex-shrink-0 bg-white border-b border-violet-50">
            <div className="max-w-3xl mx-auto px-4 py-3">
              {isSessionMode ? (
                <div className="flex items-center gap-2 text-sm min-w-0">
                  <span className="text-gray-400 flex-shrink-0">자료</span>
                  <span className="font-medium text-gray-700 truncate">
                    {activeDocTitle || '알 수 없는 자료'}
                  </span>
                  {activeDocId === null && (
                    <span className="text-xs text-amber-600 flex-shrink-0">
                      · 자료를 찾을 수 없어 이어서 질문할 수 없습니다
                    </span>
                  )}
                </div>
              ) : loadingDocs ? (
                <div className="flex items-center gap-2 text-sm text-gray-400">
                  <div className="w-3.5 h-3.5 border border-violet-200 border-t-violet-500 rounded-full animate-spin" />
                  자료 불러오는 중...
                </div>
              ) : documents.length === 0 ? (
                <p className="text-sm text-gray-400">
                  채팅 가능한 자료가 없습니다.{' '}
                  <Link to="/documents" className="text-violet-600 underline underline-offset-2 hover:text-violet-700">
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
                    className="flex-1 text-sm border border-gray-200 rounded-lg px-3 py-1.5 bg-white
                               focus:outline-none focus:border-violet-500 focus:ring-2 focus:ring-violet-200
                               hover:border-gray-300 transition-all duration-150"
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
              {/* 빈 상태 안내 (새 대화에서만) */}
              {!isSessionMode && messages.length === 0 && !loadingDocs && (
                <div className="flex items-center justify-center h-48">
                  <p className="text-sm text-gray-400 text-center">
                    {documents.length === 0
                      ? '먼저 자료를 업로드하고 처리가 완료되면 질문할 수 있습니다.'
                      : selectedDocId
                        ? '질문을 입력해 새 대화를 시작하세요.'
                        : '위에서 자료를 선택하면 질문을 시작할 수 있습니다.'}
                  </p>
                </div>
              )}

              {/* 대화 메시지 — animate-msg-in으로 각 메시지가 부드럽게 등장 */}
              {messages.map((msg, i) => (
                <div
                  key={i}
                  className={`flex animate-msg-in ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
                >
                  <div
                    className={`max-w-[75%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed whitespace-pre-wrap ${
                      msg.role === 'user'
                        ? 'bg-violet-600 text-white rounded-br-sm'
                        : 'bg-white border border-violet-100 text-gray-800 rounded-bl-sm shadow-sm'
                    }`}
                  >
                    {msg.content}
                  </div>
                </div>
              ))}

              {/* fetch 헤더 도착 전 대기 스피너 */}
              {sending && streamingContent === null && (
                <div className="flex justify-start animate-msg-in">
                  <div className="bg-white border border-violet-100 rounded-2xl rounded-bl-sm px-4 py-3.5 shadow-sm">
                    <div className="flex gap-1 items-center">
                      <span className="w-1.5 h-1.5 bg-violet-300 rounded-full animate-bounce [animation-delay:0ms]" />
                      <span className="w-1.5 h-1.5 bg-violet-300 rounded-full animate-bounce [animation-delay:150ms]" />
                      <span className="w-1.5 h-1.5 bg-violet-300 rounded-full animate-bounce [animation-delay:300ms]" />
                    </div>
                  </div>
                </div>
              )}

              {/* 스트리밍 중 — 토큰이 점점 채워지는 AI 말풍선 */}
              {streamingContent !== null && (
                <div className="flex justify-start animate-msg-in">
                  <div className="max-w-[75%] bg-white border border-violet-100 rounded-2xl rounded-bl-sm px-4 py-2.5 text-sm leading-relaxed whitespace-pre-wrap text-gray-800 shadow-sm">
                    {streamingContent || (
                      <div className="flex gap-1 items-center py-1">
                        <span className="w-1.5 h-1.5 bg-violet-300 rounded-full animate-bounce [animation-delay:0ms]" />
                        <span className="w-1.5 h-1.5 bg-violet-300 rounded-full animate-bounce [animation-delay:150ms]" />
                        <span className="w-1.5 h-1.5 bg-violet-300 rounded-full animate-bounce [animation-delay:300ms]" />
                      </div>
                    )}
                  </div>
                </div>
              )}

              {/* 에러 메시지 — AI 말풍선 스타일로 인라인 표시 */}
              {error && (
                <div className="flex justify-start animate-msg-in">
                  <div className="max-w-[75%] bg-red-50 border border-red-200 text-red-600 rounded-2xl rounded-bl-sm px-4 py-2.5 text-sm leading-relaxed">
                    {error}
                  </div>
                </div>
              )}

              <div ref={bottomRef} />
            </div>
          </div>

          {/* 입력 영역 */}
          <div className="flex-shrink-0 bg-white border-t border-violet-100">
            <div className="max-w-3xl mx-auto px-4 py-3">
              <form onSubmit={handleSend} className="flex gap-2">
                <input
                  type="text"
                  placeholder={
                    canSend
                      ? '질문을 입력하세요'
                      : isSessionMode
                        ? '이 대화의 자료를 찾을 수 없습니다'
                        : '자료를 먼저 선택하세요'
                  }
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  disabled={!canSend || sending}
                  className="input-field flex-1 disabled:bg-gray-50 disabled:text-gray-400 disabled:cursor-not-allowed"
                />
                <button
                  type="submit"
                  disabled={!canSend || !input.trim() || sending}
                  className="btn-primary flex-shrink-0 px-5"
                >
                  전송
                </button>
              </form>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

// ── 사이드바 에러 박스 ──
function SidebarError({ message }: { message: string }) {
  return (
    <div className="flex items-start gap-2 text-xs text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2 mb-2">
      <svg className="w-3.5 h-3.5 flex-shrink-0 mt-0.5" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z" />
      </svg>
      <span>{message}</span>
    </div>
  );
}

// ── 사이드바 대화 항목 ──
// 선택(열기) 버튼과 삭제 버튼은 형제로 둔다(button 중첩 금지). 삭제는 모바일에선 항상,
// 데스크톱에선 hover/focus 시 노출한다.
interface SessionItemProps {
  session: ChatSessionSummary;
  active: boolean;
  opening: boolean;
  deleting: boolean;
  disabled: boolean;
  onOpen: (sessionId: number) => void;
  onDelete: (sessionId: number, e: React.MouseEvent) => void;
}

function SessionItem({ session, active, opening, deleting, disabled, onOpen, onDelete }: SessionItemProps) {
  return (
    <li
      className={`group relative rounded-lg border transition-colors ${
        active ? 'bg-violet-50 border-violet-300' : 'border-transparent hover:bg-violet-50/50'
      }`}
    >
      <button
        type="button"
        onClick={() => onOpen(session.sessionId)}
        disabled={disabled}
        className="w-full text-left px-3 py-2.5 pr-9 disabled:cursor-not-allowed"
      >
        <p className="text-sm font-medium text-gray-800 truncate">{session.title || '제목 없음'}</p>
        <p className="text-xs text-gray-400 truncate mt-0.5">{session.documentTitle}</p>
        <p className="text-[11px] text-gray-300 mt-0.5">{formatSessionDate(session.createdAt)}</p>
      </button>

      <button
        type="button"
        onClick={(e) => onDelete(session.sessionId, e)}
        disabled={deleting}
        className="absolute top-2 right-2 p-1 rounded-md text-gray-300 hover:text-red-500 hover:bg-red-50
                   opacity-100 md:opacity-0 md:group-hover:opacity-100 focus:opacity-100 transition-opacity
                   disabled:opacity-60"
        aria-label="대화 삭제"
      >
        {deleting ? (
          <div className="w-3.5 h-3.5 border border-red-200 border-t-red-500 rounded-full animate-spin" />
        ) : (
          <svg className="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" d="M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 0 1-2.244 2.077H8.084a2.25 2.25 0 0 1-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 0 0-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 0 1 3.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 0 0-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 0 0-7.5 0" />
          </svg>
        )}
      </button>

      {/* 열기 진행 중 — 항목 위에 옅은 스피너 오버레이 */}
      {opening && (
        <div className="absolute inset-0 flex items-center justify-center bg-white/50 rounded-lg">
          <div className="w-4 h-4 border border-violet-200 border-t-violet-500 rounded-full animate-spin" />
        </div>
      )}
    </li>
  );
}
