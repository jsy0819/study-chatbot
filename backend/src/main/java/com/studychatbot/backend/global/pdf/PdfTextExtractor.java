package com.studychatbot.backend.global.pdf;

import com.studychatbot.backend.global.exception.PdfProcessingException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * PDFBox를 직접 다루는 로직을 한 곳에 격리.
 * 추출 엔진 교체 시 이 파일만 수정하면 된다.
 */
@Component
public class PdfTextExtractor {

    public String extract(MultipartFile file) {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(file.getInputStream()))) {
            if (document.isEncrypted()) {
                throw new PdfProcessingException("암호화된 PDF는 처리할 수 없습니다.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            if (text == null || text.isBlank()) {
                throw new PdfProcessingException("텍스트를 추출할 수 없습니다. 스캔 이미지 기반 PDF일 수 있습니다.");
            }

            return text.trim();
        } catch (PdfProcessingException e) {
            throw e;
        } catch (IOException e) {
            throw new PdfProcessingException("PDF 파일을 읽는 중 오류가 발생했습니다.", e);
        }
    }
}
