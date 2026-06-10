import { useEffect, useState } from 'react';
import { AxiosError } from 'axios';
import { Link, useNavigate } from 'react-router';
import apiClient from '../api/client';
import type {
  ApiResponse,
  DocumentResponse,
  QuestionCount,
  QuizGenerationRequest,
  QuizResultQuestion,
  QuizSessionCreateResponse,
  QuizSessionDetailResponse,
  QuizSessionSummary,
  QuizSubmitRequest,
  QuizSubmitResponse,
} from '../types/api';

// 한 페이지 안에서 단계별로 전환되는 화면 상태
// record: 기록에서 열어본 지난 세션 상세 (result와 같은 ResultView를 공유)
type Phase = 'setup' | 'solving' | 'result' | 'record';

// LocalDateTime(ISO, 타임존 없음) 문자열을 한국어 날짜·시각으로 표시
function formatDateTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

const QUESTION_COUNTS: QuestionCount[] = [3, 5, 10];

// axios 에러에서 사용자용 메시지를 만든다. 429(쿼터)는 별도 안내.
function quizErrorMessage(err: unknown, fallback: string): string {
  if (err instanceof AxiosError) {
    const status = err.response?.status;
    if (status === 429) return '요청이 많아 잠시 후 다시 시도해 주세요. (LLM 사용량 한도)';
    if (status === 403) return '이 자료에 대한 접근 권한이 없습니다.';
    if (status === 404) return '자료 또는 퀴즈 세션을 찾을 수 없습니다.';
    if (status === 422) return '퀴즈를 만들 수 없습니다. 자료 내용을 확인하거나 다시 시도해 주세요.';
  }
  return fallback;
}

export default function QuizPage() {
  const navigate = useNavigate();

  const [phase, setPhase] = useState<Phase>('setup');

  // ── setup 단계 상태 ──
  const [documents, setDocuments] = useState<DocumentResponse[]>([]);
  const [loadingDocs, setLoadingDocs] = useState(true);
  const [selectedDocId, setSelectedDocId] = useState<number | null>(null);
  const [questionCount, setQuestionCount] = useState<QuestionCount>(3);
  const [generating, setGenerating] = useState(false);
  const [setupError, setSetupError] = useState('');

  // ── solving 단계 상태 ──
  const [session, setSession] = useState<QuizSessionCreateResponse | null>(null);
  // questionId → 선택한 보기 인덱스
  const [answers, setAnswers] = useState<Record<number, number>>({});
  const [submitting, setSubmitting] = useState(false);
  const [solvingError, setSolvingError] = useState('');

  // ── result 단계 상태 ──
  const [result, setResult] = useState<QuizSubmitResponse | null>(null);

  // ── 지난 퀴즈 기록(setup 하단) 상태 ──
  const [sessions, setSessions] = useState<QuizSessionSummary[]>([]);
  const [loadingSessions, setLoadingSessions] = useState(true);
  const [sessionsError, setSessionsError] = useState('');
  // 상세 로딩 중인 세션 id — 해당 항목에만 스피너 표시
  const [openingSessionId, setOpeningSessionId] = useState<number | null>(null);
  // record 단계에서 보여줄 지난 세션 상세
  const [detail, setDetail] = useState<QuizSessionDetailResponse | null>(null);

  useEffect(() => {
    if (!localStorage.getItem('accessToken')) {
      navigate('/login');
      return;
    }
    fetchDocuments();
    fetchSessions();
  }, [navigate]);

  // ChatPage와 동일하게 DONE 상태 자료만 노출 (퀴즈 생성은 벡터 임베딩이 끝나야 가능)
  const fetchDocuments = async () => {
    setLoadingDocs(true);
    try {
      const { data: res } = await apiClient.get<ApiResponse<DocumentResponse[]>>('/api/documents');
      setDocuments(res.data.filter((d) => d.status === 'DONE'));
    } catch {
      // 빈 목록으로 표시 — 사용자가 안내 문구로 상황 파악
    } finally {
      setLoadingDocs(false);
    }
  };

  // 내 퀴즈 세션 목록을 불러온다 (최신순은 백엔드가 보장).
  const fetchSessions = async () => {
    setLoadingSessions(true);
    setSessionsError('');
    try {
      const { data: res } = await apiClient.get<ApiResponse<QuizSessionSummary[]>>('/api/quiz/sessions');
      setSessions(res.data);
    } catch (err) {
      setSessionsError(quizErrorMessage(err, '기록을 불러오지 못했습니다.'));
    } finally {
      setLoadingSessions(false);
    }
  };

  // 기록 항목 클릭 → 상세 조회 후 record 화면으로 전환
  const handleOpenSession = async (sessionId: number) => {
    if (openingSessionId) return;
    setOpeningSessionId(sessionId);
    setSessionsError('');
    try {
      const { data: res } = await apiClient.get<ApiResponse<QuizSessionDetailResponse>>(
        `/api/quiz/sessions/${sessionId}`,
      );
      setDetail(res.data);
      setPhase('record');
    } catch (err) {
      setSessionsError(quizErrorMessage(err, '퀴즈 상세를 불러오지 못했습니다.'));
    } finally {
      setOpeningSessionId(null);
    }
  };

  // record → setup 으로 복귀 (목록으로 돌아가기)
  const handleBackToList = () => {
    setDetail(null);
    setPhase('setup');
  };

  const handleGenerate = async () => {
    if (!selectedDocId || generating) return;
    setGenerating(true);
    setSetupError('');
    try {
      const body: QuizGenerationRequest = { documentId: selectedDocId, questionCount };
      const { data: res } = await apiClient.post<ApiResponse<QuizSessionCreateResponse>>(
        '/api/quiz/generate',
        body,
      );
      setSession(res.data);
      setAnswers({});
      setSolvingError('');
      setPhase('solving');
    } catch (err) {
      setSetupError(quizErrorMessage(err, '퀴즈 생성에 실패했습니다. 다시 시도해 주세요.'));
    } finally {
      setGenerating(false);
    }
  };

  const handleSelectAnswer = (questionId: number, choiceIndex: number) => {
    setAnswers((prev) => ({ ...prev, [questionId]: choiceIndex }));
  };

  const handleSubmit = async () => {
    if (!session || submitting) return;

    // 모든 문제에 답했는지 검증
    const unanswered = session.questions.filter((q) => answers[q.id] === undefined);
    if (unanswered.length > 0) {
      setSolvingError(`아직 답하지 않은 문제가 ${unanswered.length}개 있습니다.`);
      return;
    }

    setSubmitting(true);
    setSolvingError('');
    try {
      const body: QuizSubmitRequest = {
        answers: session.questions.map((q) => ({
          questionId: q.id,
          selectedIndex: answers[q.id],
        })),
      };
      const { data: res } = await apiClient.post<ApiResponse<QuizSubmitResponse>>(
        `/api/quiz/${session.sessionId}/submit`,
        body,
      );
      setResult(res.data);
      setPhase('result');
    } catch (err) {
      setSolvingError(quizErrorMessage(err, '제출에 실패했습니다. 다시 시도해 주세요.'));
    } finally {
      setSubmitting(false);
    }
  };

  // result → setup 으로 초기화 (다시 풀기). 방금 제출한 세션이 기록에 보이도록 목록을 갱신한다.
  const handleRestart = () => {
    setSession(null);
    setAnswers({});
    setResult(null);
    setSolvingError('');
    setSetupError('');
    setPhase('setup');
    fetchSessions();
  };

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    navigate('/login');
  };

  return (
    <div className="min-h-screen bg-surface">
      {/* 헤더 — 다른 화면과 동일한 네비게이션 패턴 */}
      <header className="bg-white border-b border-violet-100">
        <div className="max-w-3xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link to="/" className="font-semibold text-gray-900 hover:text-violet-600 transition-colors">
              스터디 챗봇
            </Link>
            <span className="text-violet-100">|</span>
            <span className="text-sm text-gray-500 font-medium">퀴즈</span>
          </div>
          <div className="flex items-center gap-4">
            <Link to="/chat" className="nav-link">채팅</Link>
            <Link to="/documents" className="nav-link">내 자료</Link>
            <button onClick={handleLogout} className="nav-link">로그아웃</button>
          </div>
        </div>
      </header>

      <main className="max-w-3xl mx-auto px-4 py-8">
        {phase === 'setup' && (
          <div className="space-y-6">
            <SetupView
              documents={documents}
              loadingDocs={loadingDocs}
              selectedDocId={selectedDocId}
              onSelectDoc={setSelectedDocId}
              questionCount={questionCount}
              onSelectCount={setQuestionCount}
              generating={generating}
              error={setupError}
              onGenerate={handleGenerate}
            />
            <HistorySection
              sessions={sessions}
              loading={loadingSessions}
              error={sessionsError}
              openingSessionId={openingSessionId}
              onOpen={handleOpenSession}
            />
          </div>
        )}

        {phase === 'solving' && session && (
          <SolvingView
            session={session}
            answers={answers}
            onSelectAnswer={handleSelectAnswer}
            submitting={submitting}
            error={solvingError}
            onSubmit={handleSubmit}
          />
        )}

        {phase === 'result' && result && (
          <ResultView
            documentTitle={documents.find((d) => d.id === selectedDocId)?.title ?? ''}
            score={result.score}
            totalCount={result.totalCount}
            questions={result.results}
            actionLabel="다시 풀기"
            onAction={handleRestart}
          />
        )}

        {phase === 'record' && detail && (
          <ResultView
            documentTitle={detail.documentTitle}
            score={detail.score}
            totalCount={detail.questionCount}
            questions={detail.questions}
            actionLabel="목록으로 돌아가기"
            onAction={handleBackToList}
          />
        )}
      </main>
    </div>
  );
}

// ── 공통 에러 박스 (DocumentsPage 스타일 재사용) ──
function ErrorBox({ message }: { message: string }) {
  return (
    <div className="flex items-center gap-2 text-sm text-red-600 bg-red-50 border border-red-100 rounded-lg px-3 py-2">
      <svg className="w-4 h-4 flex-shrink-0" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 1 1-18 0 9 9 0 0 1 18 0Zm-9 3.75h.008v.008H12v-.008Z" />
      </svg>
      <span>{message}</span>
    </div>
  );
}

// ── setup: 자료·문제 수 선택 후 생성 ──
interface SetupViewProps {
  documents: DocumentResponse[];
  loadingDocs: boolean;
  selectedDocId: number | null;
  onSelectDoc: (id: number | null) => void;
  questionCount: QuestionCount;
  onSelectCount: (count: QuestionCount) => void;
  generating: boolean;
  error: string;
  onGenerate: () => void;
}

function SetupView({
  documents,
  loadingDocs,
  selectedDocId,
  onSelectDoc,
  questionCount,
  onSelectCount,
  generating,
  error,
  onGenerate,
}: SetupViewProps) {
  return (
    <div className="card p-6 space-y-6">
      <div>
        <h2 className="text-base font-semibold text-gray-900">퀴즈 만들기</h2>
        <p className="text-sm text-gray-400 mt-1">자료를 골라 4지선다 퀴즈를 생성합니다.</p>
      </div>

      {/* 자료 선택 */}
      <div className="space-y-2">
        <label htmlFor="quiz-doc" className="block text-sm text-gray-500">학습 자료</label>
        {loadingDocs ? (
          <div className="flex items-center gap-2 text-sm text-gray-400">
            <div className="w-3.5 h-3.5 border border-violet-200 border-t-violet-500 rounded-full animate-spin" />
            자료 불러오는 중...
          </div>
        ) : documents.length === 0 ? (
          <p className="text-sm text-gray-400">
            퀴즈로 만들 자료가 없습니다.{' '}
            <Link to="/documents" className="text-violet-600 underline underline-offset-2 hover:text-violet-700">
              자료 추가하기
            </Link>
          </p>
        ) : (
          <select
            id="quiz-doc"
            value={selectedDocId ?? ''}
            onChange={(e) => onSelectDoc(e.target.value ? Number(e.target.value) : null)}
            className="input-field"
          >
            <option value="">자료를 선택하세요</option>
            {documents.map((doc) => (
              <option key={doc.id} value={doc.id}>{doc.title}</option>
            ))}
          </select>
        )}
      </div>

      {/* 문제 수 선택 */}
      <div className="space-y-2">
        <span className="block text-sm text-gray-500">문제 수</span>
        <div className="flex gap-2">
          {QUESTION_COUNTS.map((count) => (
            <button
              key={count}
              type="button"
              onClick={() => onSelectCount(count)}
              className={`flex-1 py-2.5 text-sm rounded-lg border transition-colors ${
                questionCount === count
                  ? 'bg-violet-600 text-white border-violet-600 font-semibold'
                  : 'bg-white text-gray-600 border-gray-200 hover:border-violet-400'
              }`}
            >
              {count}문제
            </button>
          ))}
        </div>
      </div>

      {error && <ErrorBox message={error} />}

      <button
        type="button"
        onClick={onGenerate}
        disabled={!selectedDocId || generating}
        className="btn-primary w-full"
      >
        {generating ? '퀴즈 생성 중...' : '퀴즈 생성'}
      </button>
    </div>
  );
}

// ── solving: 문제 풀이 ──
interface SolvingViewProps {
  session: QuizSessionCreateResponse;
  answers: Record<number, number>;
  onSelectAnswer: (questionId: number, choiceIndex: number) => void;
  submitting: boolean;
  error: string;
  onSubmit: () => void;
}

function SolvingView({ session, answers, onSelectAnswer, submitting, error, onSubmit }: SolvingViewProps) {
  const answeredCount = session.questions.filter((q) => answers[q.id] !== undefined).length;
  const total = session.questions.length;

  return (
    <div className="space-y-6">
      {/* 진행 상황 */}
      <div className="card p-4 flex items-center justify-between">
        <span className="text-sm font-medium text-gray-700">문제 풀이</span>
        <span className="text-sm text-gray-400">{answeredCount} / {total} 답변</span>
      </div>

      {session.questions.map((q) => (
        <div key={q.id} className="card p-6">
          <p className="text-sm font-semibold text-gray-900 mb-4">
            <span className="text-violet-600">Q{q.questionOrder}.</span> {q.question}
          </p>
          <div className="space-y-2">
            {q.choices.map((choice, idx) => {
              const selected = answers[q.id] === idx;
              return (
                <button
                  key={idx}
                  type="button"
                  onClick={() => onSelectAnswer(q.id, idx)}
                  className={`w-full text-left px-4 py-2.5 text-sm rounded-lg border transition-colors ${
                    selected
                      ? 'bg-violet-50 border-violet-400 text-violet-800 font-medium'
                      : 'bg-white border-gray-200 text-gray-700 hover:border-violet-300 hover:bg-violet-50/30'
                  }`}
                >
                  <span className="inline-block w-5 text-gray-400">{idx + 1}.</span>
                  {choice}
                </button>
              );
            })}
          </div>
        </div>
      ))}

      {error && <ErrorBox message={error} />}

      <button
        type="button"
        onClick={onSubmit}
        disabled={submitting}
        className="btn-primary w-full"
      >
        {submitting ? '채점 중...' : '제출하기'}
      </button>
    </div>
  );
}

// ── result: 채점 결과 ──
// 제출 직후(result)와 기록 상세(record)가 공유하는 결과 UI.
// 데이터 출처(QuizSubmitResponse / QuizSessionDetailResponse)마다 필드명이 달라
// 객체 대신 정규화된 값들을 받고, 하단 버튼만 상황에 맞게(actionLabel/onAction) 바꾼다.
interface ResultViewProps {
  documentTitle: string;
  score: number | null;
  totalCount: number;
  questions: QuizResultQuestion[];
  actionLabel: string;
  onAction: () => void;
}

function ResultView({ documentTitle, score, totalCount, questions, actionLabel, onAction }: ResultViewProps) {
  // 응답 누락 시에도 .map 폭발을 막는다.
  const items = questions ?? [];
  return (
    <div className="space-y-6">
      {/* 점수 요약 */}
      <div className="card p-8 text-center">
        <p className="text-sm text-gray-400 mb-1">{documentTitle}</p>
        <p className="text-3xl font-bold text-violet-600">
          {score} <span className="text-gray-300">/</span> {totalCount}
        </p>
        <p className="text-sm text-gray-500 mt-2">
          {totalCount}문제 중 {score}문제 정답
        </p>
      </div>

      {/* 문제별 결과 */}
      {items.map((q) => {
        const isCorrect = q.correct;
        return (
          <div
            key={q.questionId}
            className={`card p-6 border-l-4 ${isCorrect ? 'border-l-green-400' : 'border-l-red-400'}`}
          >
            <div className="flex items-start justify-between gap-3 mb-4">
              <p className="text-sm font-semibold text-gray-900">
                <span className="text-violet-600">Q{q.questionOrder}.</span> {q.question}
              </p>
              <span
                className={`flex-shrink-0 text-xs px-2 py-0.5 rounded-full font-medium ${
                  isCorrect
                    ? 'bg-green-50 text-green-700 border border-green-100'
                    : 'bg-red-50 text-red-500 border border-red-100'
                }`}
              >
                {isCorrect ? '정답' : '오답'}
              </span>
            </div>

            <div className="space-y-2">
              {q.choices.map((choice, idx) => {
                const isAnswer = q.answerIndex === idx;
                const isMyPick = q.userAnswerIndex === idx;
                // 정답=초록, 내가 고른 오답=빨강, 나머지=기본
                let style = 'bg-white border-gray-200 text-gray-600';
                if (isAnswer) style = 'bg-green-50 border-green-300 text-green-800 font-medium';
                else if (isMyPick) style = 'bg-red-50 border-red-300 text-red-700 font-medium';

                return (
                  <div key={idx} className={`flex items-center gap-2 px-4 py-2.5 text-sm rounded-lg border ${style}`}>
                    <span className="inline-block w-5 text-gray-400">{idx + 1}.</span>
                    <span className="flex-1">{choice}</span>
                    {isAnswer && <span className="text-xs text-green-600 font-medium">정답</span>}
                    {isMyPick && !isAnswer && <span className="text-xs text-red-500 font-medium">내 선택</span>}
                  </div>
                );
              })}
            </div>

            {/* 해설 */}
            <div className="mt-4 pt-4 border-t border-violet-50">
              <p className="text-xs font-medium text-gray-400 mb-1">해설</p>
              <p className="text-sm text-gray-700 leading-relaxed whitespace-pre-wrap">{q.explanation}</p>
            </div>
          </div>
        );
      })}

      <button type="button" onClick={onAction} className="btn-primary w-full">
        {actionLabel}
      </button>
    </div>
  );
}

// ── 지난 퀴즈 기록 (setup 하단) ──
// 제출 완료된 세션만 노출한다 — 미제출(생성만 하고 중단) 세션은 점수·정답이 없어 기록이 아니다.
interface HistorySectionProps {
  sessions: QuizSessionSummary[];
  loading: boolean;
  error: string;
  openingSessionId: number | null;
  onOpen: (sessionId: number) => void;
}

function HistorySection({ sessions, loading, error, openingSessionId, onOpen }: HistorySectionProps) {
  const submitted = sessions.filter((s) => s.submittedAt !== null);

  return (
    <div className="card p-6 space-y-4">
      <div>
        <h2 className="text-base font-semibold text-gray-900">지난 퀴즈 기록</h2>
        <p className="text-sm text-gray-400 mt-1">제출한 퀴즈를 다시 열어 정답·해설을 볼 수 있습니다.</p>
      </div>

      {error && <ErrorBox message={error} />}

      {loading ? (
        <div className="flex items-center gap-2 text-sm text-gray-400">
          <div className="w-3.5 h-3.5 border border-violet-200 border-t-violet-500 rounded-full animate-spin" />
          기록 불러오는 중...
        </div>
      ) : submitted.length === 0 ? (
        <p className="text-sm text-gray-400">아직 푼 퀴즈가 없습니다.</p>
      ) : (
        <ul className="space-y-2">
          {submitted.map((s) => {
            const opening = openingSessionId === s.sessionId;
            return (
              <li key={s.sessionId}>
                <button
                  type="button"
                  onClick={() => onOpen(s.sessionId)}
                  disabled={openingSessionId !== null}
                  className="w-full text-left px-4 py-3 rounded-lg border border-gray-200 bg-white hover:border-violet-300 hover:bg-violet-50/30 transition-colors disabled:opacity-60 flex items-center justify-between gap-3"
                >
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">{s.documentTitle}</p>
                    <p className="text-xs text-gray-400 mt-0.5">
                      {s.submittedAt ? formatDateTime(s.submittedAt) : ''}
                    </p>
                  </div>
                  <div className="flex items-center gap-2 flex-shrink-0">
                    <span className="text-sm font-semibold text-violet-600">
                      {s.score} <span className="text-gray-300">/</span> {s.questionCount}
                    </span>
                    {opening && (
                      <div className="w-3.5 h-3.5 border border-violet-200 border-t-violet-500 rounded-full animate-spin" />
                    )}
                  </div>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
