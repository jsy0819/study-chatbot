package com.studychatbot.backend.domain.document.service;

import com.studychatbot.backend.domain.document.entity.Document;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentVectorService {

    // 토큰 기준 512개 청크. 너무 크면 임베딩 품질 저하, 너무 작으면 문맥 손실.
    private static final int CHUNK_SIZE = 512;

    private final VectorStore vectorStore;

    /**
     * Document 텍스트를 청크로 분할하고 임베딩하여 벡터 스토어에 저장한다.
     * 각 청크에 documentId / userId 메타데이터를 붙여 나중에 사용자·자료 단위 필터링 검색이 가능하게 한다.
     */
    public void embed(Document document) {
        List<org.springframework.ai.document.Document> chunks = split(document);
        vectorStore.add(chunks);
        log.info("벡터 저장 완료 — documentId={}, 청크 수={}", document.getId(), chunks.size());
    }

    /**
     * 자료 삭제 시 연결된 벡터 청크도 함께 제거한다.
     * VectorStore.delete(String) 필터 표현식으로 해당 자료 청크만 삭제한다.
     */
    public void deleteByDocument(Long documentId) {
        vectorStore.delete("documentId == '" + documentId + "'");
        log.info("벡터 삭제 완료 — documentId={}", documentId);
    }

    private List<org.springframework.ai.document.Document> split(Document document) {
        org.springframework.ai.document.Document aiDoc = new org.springframework.ai.document.Document(
                document.getContent(),
                Map.of(
                        "documentId", String.valueOf(document.getId()),
                        "userId", String.valueOf(document.getUser().getId()),
                        "title", document.getTitle()
                )
        );

        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(CHUNK_SIZE)
                .withMinChunkSizeChars(50)
                .withMinChunkLengthToEmbed(10)
                .withMaxNumChunks(1000)
                .withKeepSeparator(true)
                .build();

        return splitter.apply(List.of(aiDoc));
    }
}
