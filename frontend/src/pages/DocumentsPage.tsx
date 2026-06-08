import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import apiClient from '../api/client';
import type { ApiResponse, DocumentResponse } from '../types/api';

type UploadTab = 'text' | 'pdf';

export default function DocumentsPage() {
  const navigate = useNavigate();

  const [documents, setDocuments] = useState<DocumentResponse[]>([]);
  const [loadingList, setLoadingList] = useState(true);
  const [listError, setListError] = useState('');

  const [tab, setTab] = useState<UploadTab>('text');

  const [textTitle, setTextTitle] = useState('');
  const [textContent, setTextContent] = useState('');
  const [submittingText, setSubmittingText] = useState(false);
  const [textError, setTextError] = useState('');

  const [pdfTitle, setPdfTitle] = useState('');
  const [pdfFile, setPdfFile] = useState<File | null>(null);
  const [submittingPdf, setSubmittingPdf] = useState(false);
  const [pdfError, setPdfError] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!localStorage.getItem('accessToken')) {
      navigate('/login');
      return;
    }
    fetchDocuments();
  }, [navigate]);

  const fetchDocuments = async () => {
    setLoadingList(true);
    setListError('');
    try {
      const { data: res } = await apiClient.get<ApiResponse<DocumentResponse[]>>('/api/documents');
      setDocuments(res.data);
    } catch {
      setListError('자료 목록을 불러오지 못했습니다.');
    } finally {
      setLoadingList(false);
    }
  };

  const handleTextSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmittingText(true);
    setTextError('');
    try {
      await apiClient.post<ApiResponse<DocumentResponse>>('/api/documents', {
        title: textTitle.trim(),
        content: textContent.trim(),
      });
      setTextTitle('');
      setTextContent('');
      await fetchDocuments();
    } catch {
      setTextError('자료 저장에 실패했습니다.');
    } finally {
      setSubmittingText(false);
    }
  };

  const handlePdfSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!pdfFile) return;
    setSubmittingPdf(true);
    setPdfError('');
    try {
      const formData = new FormData();
      formData.append('file', pdfFile);
      if (pdfTitle.trim()) formData.append('title', pdfTitle.trim());
      // client.ts 인터셉터가 FormData 감지 시 Content-Type 삭제 → axios가 boundary 포함 multipart 헤더 자동 설정
      await apiClient.post<ApiResponse<DocumentResponse>>('/api/documents/pdf', formData);
      setPdfTitle('');
      setPdfFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
      await fetchDocuments();
    } catch {
      setPdfError('PDF 업로드에 실패했습니다.');
    } finally {
      setSubmittingPdf(false);
    }
  };

  const handleDelete = async (id: number) => {
    if (!window.confirm('자료를 삭제할까요?')) return;
    try {
      await apiClient.delete(`/api/documents/${id}`);
      setDocuments((prev) => prev.filter((d) => d.id !== id));
    } catch {
      alert('삭제에 실패했습니다.');
    }
  };

  const handleLogout = () => {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    navigate('/login');
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="bg-white border-b border-gray-200">
        <div className="max-w-3xl mx-auto px-4 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <Link to="/" className="font-semibold text-gray-900 hover:text-gray-600 transition-colors">
              스터디 챗봇
            </Link>
            <span className="text-gray-200">|</span>
            <span className="text-sm text-gray-500 font-medium">내 자료</span>
          </div>
          <button
            onClick={handleLogout}
            className="text-sm text-gray-500 hover:text-gray-900 transition-colors"
          >
            로그아웃
          </button>
        </div>
      </header>

      <main className="max-w-3xl mx-auto px-4 py-8 space-y-6">
        {/* 자료 추가 */}
        <div className="bg-white rounded-2xl border border-gray-200 p-6">
          <h2 className="text-base font-semibold text-gray-900 mb-4">자료 추가</h2>

          {/* 탭 */}
          <div className="flex gap-1 mb-5 bg-gray-100 rounded-lg p-1 w-fit">
            {(['text', 'pdf'] as const).map((t) => (
              <button
                key={t}
                onClick={() => setTab(t)}
                className={`px-4 py-1.5 text-sm rounded-md transition-colors ${
                  tab === t
                    ? 'bg-white text-gray-900 font-medium shadow-sm'
                    : 'text-gray-500 hover:text-gray-700'
                }`}
              >
                {t === 'text' ? '텍스트' : 'PDF'}
              </button>
            ))}
          </div>

          {/* 텍스트 업로드 폼 */}
          {tab === 'text' && (
            <form onSubmit={handleTextSubmit} className="space-y-3">
              <input
                type="text"
                placeholder="제목"
                value={textTitle}
                onChange={(e) => setTextTitle(e.target.value)}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900 focus:border-transparent"
                required
              />
              <textarea
                placeholder="자료 내용을 입력하세요"
                value={textContent}
                onChange={(e) => setTextContent(e.target.value)}
                rows={5}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900 focus:border-transparent resize-none"
                required
              />
              {textError && <p className="text-xs text-red-500">{textError}</p>}
              <button
                type="submit"
                disabled={submittingText}
                className="w-full bg-gray-900 text-white text-sm rounded-lg py-2 hover:bg-gray-700 transition-colors disabled:opacity-50"
              >
                {submittingText ? '저장 중...' : '자료 저장'}
              </button>
            </form>
          )}

          {/* PDF 업로드 폼 */}
          {tab === 'pdf' && (
            <form onSubmit={handlePdfSubmit} className="space-y-3">
              <input
                type="text"
                placeholder="제목 (선택 — 비워두면 파일명으로 자동 설정)"
                value={pdfTitle}
                onChange={(e) => setPdfTitle(e.target.value)}
                className="w-full border border-gray-200 rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-gray-900 focus:border-transparent"
              />
              <label className="block cursor-pointer">
                <div
                  className={`border-2 border-dashed rounded-lg p-6 text-center transition-colors ${
                    pdfFile ? 'border-gray-400 bg-gray-50' : 'border-gray-200 hover:border-gray-400'
                  }`}
                >
                  {pdfFile ? (
                    <p className="text-sm text-gray-900 font-medium">{pdfFile.name}</p>
                  ) : (
                    <>
                      <p className="text-sm text-gray-400">클릭하여 PDF 파일 선택</p>
                      <p className="text-xs text-gray-300 mt-1">.pdf 형식만 지원</p>
                    </>
                  )}
                </div>
                <input
                  ref={fileInputRef}
                  type="file"
                  accept=".pdf"
                  className="sr-only"
                  onChange={(e) => setPdfFile(e.target.files?.[0] ?? null)}
                  required
                />
              </label>
              {pdfError && <p className="text-xs text-red-500">{pdfError}</p>}
              <button
                type="submit"
                disabled={submittingPdf || !pdfFile}
                className="w-full bg-gray-900 text-white text-sm rounded-lg py-2 hover:bg-gray-700 transition-colors disabled:opacity-50"
              >
                {submittingPdf ? '업로드 중...' : 'PDF 업로드'}
              </button>
            </form>
          )}
        </div>

        {/* 자료 목록 */}
        <div className="bg-white rounded-2xl border border-gray-200 p-6">
          <div className="flex items-center justify-between mb-4">
            <h2 className="text-base font-semibold text-gray-900">내 자료</h2>
            <button
              onClick={fetchDocuments}
              className="text-xs text-gray-400 hover:text-gray-700 transition-colors"
            >
              새로고침
            </button>
          </div>

          {loadingList ? (
            <div className="flex justify-center py-8">
              <div className="w-5 h-5 border-2 border-gray-200 border-t-gray-900 rounded-full animate-spin" />
            </div>
          ) : listError ? (
            <p className="text-sm text-red-500 text-center py-4">{listError}</p>
          ) : documents.length === 0 ? (
            <p className="text-sm text-gray-400 text-center py-8">아직 자료가 없습니다</p>
          ) : (
            <ul className="divide-y divide-gray-100">
              {documents.map((doc) => (
                <li key={doc.id} className="py-3 flex items-center justify-between gap-4">
                  <div className="min-w-0">
                    <p className="text-sm font-medium text-gray-900 truncate">{doc.title}</p>
                    <div className="flex items-center gap-2 mt-0.5">
                      <StatusBadge status={doc.status} />
                      <span className="text-xs text-gray-400">
                        {new Date(doc.createdAt).toLocaleDateString('ko-KR')}
                      </span>
                    </div>
                  </div>
                  <button
                    onClick={() => handleDelete(doc.id)}
                    className="flex-shrink-0 text-xs text-gray-400 hover:text-red-500 transition-colors"
                  >
                    삭제
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      </main>
    </div>
  );
}

function StatusBadge({ status }: { status: string }) {
  const styles: Record<string, { label: string; className: string }> = {
    PROCESSING: { label: '처리중', className: 'bg-blue-50 text-blue-600' },
    DONE:       { label: '완료',   className: 'bg-green-50 text-green-700' },
    FAILED:     { label: '실패',   className: 'bg-red-50 text-red-500' },
  };
  const style = styles[status] ?? { label: status, className: 'bg-gray-100 text-gray-500' };
  return (
    <span className={`text-xs px-1.5 py-0.5 rounded font-medium ${style.className}`}>
      {style.label}
    </span>
  );
}
