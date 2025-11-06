package io.github.jdeeplearn.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration class managing hybrid search weights
 * for semantic (vector) and lexical (text) components.
 */
@Component
public class FaqHybridConfig {

    @Value("${faq.hybrid.semantic-weight:0.7}")
    private double semanticWeight;

    @Value("${faq.hybrid.lexical-weight:0.3}")
    private double lexicalWeight;

    public double normalizedSemantic() {
        double sum = semanticWeight + lexicalWeight;
        return sum == 0 ? 0.7 : semanticWeight / sum;
    }

    public double normalizedLexical() {
        double sum = semanticWeight + lexicalWeight;
        return sum == 0 ? 0.3 : lexicalWeight / sum;
    }

    @Override
    public String toString() {
        return "FaqHybridConfig{semantic=" + semanticWeight + ", lexical=" + lexicalWeight + "}";
    }
}
