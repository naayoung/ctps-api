package com.ctps.ctps_api.domain.problem.service.search;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DefaultExternalProblemProviderScoreNormalizer implements ExternalProblemProviderScoreNormalizer {

    @Override
    public List<ProviderScoreSignal> normalize(List<ProviderScoreSignal> signals) {
        if (signals.isEmpty()) {
            return List.of();
        }

        double minScore = signals.stream()
                .map(ProviderScoreSignal::getRawScore)
                .filter(value -> value != null)
                .min(Comparator.naturalOrder())
                .orElse(0.0);
        double maxScore = signals.stream()
                .map(ProviderScoreSignal::getRawScore)
                .filter(value -> value != null)
                .max(Comparator.naturalOrder())
                .orElse(0.0);

        return signals.stream()
                .map(signal -> signal.toBuilder()
                        .normalizedScore(normalize(signal, minScore, maxScore))
                        .build())
                .toList();
    }

    private double normalize(ProviderScoreSignal signal, double minScore, double maxScore) {
        if (signal.getScoreSource() == ProviderScoreSource.RANK_POSITION && signal.getRank() != null && signal.getRank() > 0) {
            return clamp(1.0 / log2(signal.getRank() + 1));
        }

        if (signal.getRawScore() == null) {
            return 0.0;
        }

        if (maxScore <= 0.0 && minScore <= 0.0) {
            return 0.0;
        }

        if (Double.compare(maxScore, minScore) == 0) {
            return clamp(maxScore <= 0.0 ? 0.0 : 1.0);
        }

        return clamp((signal.getRawScore() - minScore) / (maxScore - minScore));
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2);
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
