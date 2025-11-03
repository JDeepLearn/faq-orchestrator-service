package io.github.jdeeplearn.rag.controller;

import io.github.jdeeplearn.rag.service.FaqService;
import io.github.jdeeplearn.rag.service.dto.AskRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller that handles FAQ chatbot queries.
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
    public ResponseEntity<FaqService.FaqResponse> ask(@Valid @RequestBody @NotNull AskRequest request) {
        log.info("Received question: {}", request.question());
        return ResponseEntity.ok(faqService.answer(request.question()));
    }
}
