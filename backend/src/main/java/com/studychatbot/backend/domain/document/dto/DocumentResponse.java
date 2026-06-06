package com.studychatbot.backend.domain.document.dto;

import com.studychatbot.backend.domain.document.entity.Document;
import com.studychatbot.backend.domain.document.entity.DocumentStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DocumentResponse {

    private final Long id;
    private final String title;
    private final DocumentStatus status;
    private final LocalDateTime createdAt;

    public static DocumentResponse from(Document document) {
        return DocumentResponse.builder()
                .id(document.getId())
                .title(document.getTitle())
                .status(document.getStatus())
                .createdAt(document.getCreatedAt())
                .build();
    }
}
