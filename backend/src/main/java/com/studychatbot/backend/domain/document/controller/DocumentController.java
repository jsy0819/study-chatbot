package com.studychatbot.backend.domain.document.controller;

import com.studychatbot.backend.domain.document.dto.DocumentResponse;
import com.studychatbot.backend.domain.document.dto.DocumentUploadRequest;
import com.studychatbot.backend.domain.document.service.DocumentService;
import com.studychatbot.backend.global.exception.InvalidFileException;
import com.studychatbot.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
            @PathVariable("id") Long id,
            Authentication authentication) {
        String email = authentication.getName();
        DocumentResponse response = documentService.getDocument(email, id);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    @PostMapping(value = "/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            Authentication authentication) {
        validatePdfFile(file);
        String email = authentication.getName();
        DocumentResponse response = documentService.uploadPdf(email, file, title);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(
            @PathVariable("id") Long id,
            Authentication authentication) {
        String email = authentication.getName();
        documentService.deleteDocument(email, id);
        return ResponseEntity.noContent().build();
    }

    private void validatePdfFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("파일이 비어 있습니다.");
        }
        if (!"application/pdf".equals(file.getContentType())) {
            throw new InvalidFileException("PDF 파일만 업로드할 수 있습니다.");
        }
    }
}
