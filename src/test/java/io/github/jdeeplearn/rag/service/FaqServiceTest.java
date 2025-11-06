package io.github.jdeeplearn.rag.service;

import io.github.jdeeplearn.rag.client.EmbeddingClient;
import io.github.jdeeplearn.rag.dto.EmbeddingResponse;
import io.github.jdeeplearn.rag.dto.FaqResponse;
import io.github.jdeeplearn.rag.repository.FaqRepository;
import io.github.jdeeplearn.rag.repository.FaqRepository.FaqMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.MessageSource;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for FaqService ensuring full coverage of decision logic.
 * No external Couchbase or embedding service is called.
 */
class FaqServiceTest {

    @Mock
    private FaqRepository faqRepository;

    @Mock
    private EmbeddingClient embeddingClient;

    @Mock
    private MessageSource messageSource;

    private FaqService faqService;

    private final Locale locale = Locale.ENGLISH;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        // Configure service manually (mimicking @Value injection)
        faqService = new FaqService(
                faqRepository,
                messageSource,
                embeddingClient,
                0.9, // match threshold
                0.75, // soft threshold
                "1-800-123-4567"
        );

        // Generic message source stub for fallback messages
        when(messageSource.getMessage(anyString(), any(Object[].class), any(Locale.class)))
                .thenAnswer(inv -> switch ((String) inv.getArgument(0)) {
                    case "faq.fallback.no-match" -> "No match found. Contact " + inv.getArgument(1);
                    case "faq.fallback.low-confidence" -> "Low confidence. Try rephrasing.";
                    case "faq.fallback.technical" -> "Technical issue. Try later.";
                    case "faq.fallback.empty" -> "Please ask a valid question.";
                    default -> "Default fallback.";
                });
    }

    @Test
    @DisplayName("Returns valid FAQ when high-confidence match found")
    void testHandleQuestion_HighConfidence() {
        // Mock embedding service response
        EmbeddingResponse mockEmbedding = new EmbeddingResponse("hf-local", "intfloat/e5-large-v2", 1024, List.of(0.1, 0.2, 0.3));
        when(embeddingClient.getEmbedding(anyString())).thenReturn(mockEmbedding);

        // Mock repository result
        List<FaqMatch> matches = List.of(new FaqMatch(
                "faq-1",
                0.94
        ));
        when(faqRepository.findTopKHybrid(any(float[].class), anyString()))
                .thenReturn(matches);

        FaqResponse faqResponse = new FaqResponse("Go to settings and click Reset Password.",
                "https://example.com/image.png",
                "https://example.com/link");

        when(faqRepository.getFaqDocumentById(anyString())).thenReturn(faqResponse);

        FaqResponse response = faqService.handleQuestion("How do I reset my password?", locale);

        assertThat(response.answer()).contains("Reset Password");
        assertThat(response.image()).isEqualTo("https://example.com/image.png");
        assertThat(response.link()).isEqualTo("https://example.com/link");
    }

    @Test
    @DisplayName("Returns low-confidence fallback when score below threshold")
    void testHandleQuestion_LowConfidence() {
        EmbeddingResponse mockEmbedding = new EmbeddingResponse("hf-local", "intfloat/e5-large-v2", 1024, List.of(0.1, 0.2, 0.3));
        when(embeddingClient.getEmbedding(anyString())).thenReturn(mockEmbedding);
        List<FaqMatch> matches = List.of(new FaqMatch(
                "Partial match question",
//                "Partial answer",
//                null,
//                null,
                0.8
        ));
        when(faqRepository.findTopKHybrid(any(float[].class), anyString()))
                .thenReturn(matches);

        FaqResponse response = faqService.handleQuestion("How to something unclear?", locale);

        assertThat(response.answer()).contains("Low confidence");
        verify(messageSource).getMessage(eq("faq.fallback.low-confidence"), any(), eq(locale));
    }

    @Test
    @DisplayName("Returns no-match fallback when repository returns empty result")
    void testHandleQuestion_NoMatch() {
        EmbeddingResponse mockEmbedding = new EmbeddingResponse("hf-local", "intfloat/e5-large-v2", 1024, List.of(0.1, 0.2, 0.3));
        when(embeddingClient.getEmbedding(anyString())).thenReturn(mockEmbedding);
        when(faqRepository.findTopKHybrid(any(float[].class), anyString()))
                .thenReturn(Collections.emptyList());

        FaqResponse response = faqService.handleQuestion("unknown topic", locale);

        assertThat(response.answer()).contains("No match");
        verify(messageSource).getMessage(eq("faq.fallback.no-match"), any(), eq(locale));
    }

    @Test
    @DisplayName("Returns technical fallback when embedding service throws exception")
    void testHandleQuestion_EmbeddingError() {
        when(embeddingClient.getEmbedding(anyString())).thenThrow(new RuntimeException("Service unavailable"));

        FaqResponse response = faqService.handleQuestion("error test", locale);

        assertThat(response.answer()).contains("Technical");
        verify(messageSource).getMessage(eq("faq.fallback.technical"), any(), eq(locale));
    }

    @Test
    @DisplayName("Returns empty fallback when question is blank or null")
    void testHandleQuestion_BlankQuestion() {
        FaqResponse response1 = faqService.handleQuestion("", locale);
        FaqResponse response2 = faqService.handleQuestion(null, locale);

        assertThat(response1.answer()).contains("valid question");
        assertThat(response2.answer()).contains("valid question");
        verify(messageSource, atLeastOnce()).getMessage(eq("faq.fallback.empty"), any(), eq(locale));
    }

    @Test
    @DisplayName("Returns technical fallback when embedding is empty")
    void testHandleQuestion_EmptyEmbedding() {
        EmbeddingResponse mockEmbedding = new EmbeddingResponse("hf-local", "intfloat/e5-large-v2", 1024, List.of());
        when(embeddingClient.getEmbedding(anyString())).thenReturn(mockEmbedding);

        FaqResponse response = faqService.handleQuestion("test", locale);

        assertThat(response.answer()).contains("Technical");
    }
}
