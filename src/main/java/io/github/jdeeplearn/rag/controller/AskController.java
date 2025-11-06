package io.github.jdeeplearn.rag.controller;

import io.github.jdeeplearn.rag.dto.AskRequest;
import io.github.jdeeplearn.rag.dto.FaqResponse;
import io.github.jdeeplearn.rag.service.FaqService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;

/**
 * REST API controller exposing /api/ask.
 */
@RestController
@RequestMapping("/api")
public class AskController {

    private static final Logger log = LoggerFactory.getLogger(AskController.class);
    private final FaqService faqService;

    public AskController(FaqService faqService) {
        this.faqService = faqService;
    }

    @PostMapping("/ask")
    public ResponseEntity<FaqResponse> ask(@RequestBody AskRequest request, Locale locale) {
        String question = request == null ? "" : request.question();
        log.info("Received question: '{}'", question);
        FaqResponse response = faqService.handleQuestion(question, locale);
        log.info("Response: '{}'", response.answer());
        return ResponseEntity.ok(response);
    }
}
