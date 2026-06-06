package com.studychatbot.backend.domain.document.controller;

import com.studychatbot.backend.domain.document.dto.DocumentResponse;
import com.studychatbot.backend.domain.document.dto.DocumentUploadRequest;
import com.studychatbot.backend.domain.document.service.DocumentService;
import com.studychatbot.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @PostMapping
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(
            @Valid @RequestBody DocumentUploadRequest request,
            Authentication authentication) {
        String email = authentication.getName();
        DocumentResponse response = documentService.upload(email, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> getMyDocuments(Authentication authentication) {
        String email = authentication.getName();
        List<DocumentResponse> response = documentService.getMyDocuments(email);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentResponse>> getDocument(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication.getName();
        DocumentResponse response = documentService.getDocument(email, id);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable Long id,
            Authentication authentication) {
        String email = authentication.getName();
        documentService.deleteDocument(email, id);
        return ResponseEntity.noContent().build();
    }
}
