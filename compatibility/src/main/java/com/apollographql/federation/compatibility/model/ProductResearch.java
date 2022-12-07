package com.apollographql.federation.compatibility.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class ProductResearch {
    public static final ProductResearch FEDERATION_STUDY = new ProductResearch(new CaseStudy("1234", "Federation Study"));
    public static final ProductResearch STUDIO_STUDY = new ProductResearch(new CaseStudy("1235", "Studio Study"));
    public static final List<ProductResearch> RESEARCH_LIST = List.of(FEDERATION_STUDY, STUDIO_STUDY);

    private final CaseStudy study;
    private final String outcome;

    public ProductResearch(CaseStudy study) {
        this.study = study;
        this.outcome = null;
    }

    public ProductResearch(CaseStudy study, String outcome) {
        this.study = study;
        this.outcome = outcome;
    }

    public CaseStudy getStudy() {
        return study;
    }

    public String getOutcome() {
        return outcome;
    }

    public static ProductResearch resolveReference(@NotNull Map<String, Object> reference) {
        if (reference.get("study") instanceof HashMap caseStudy) {
            if (caseStudy.get("caseNumber") instanceof String caseNumber) {
                return RESEARCH_LIST.stream()
                        .filter(research -> research.study.getCaseNumber().equals(caseNumber))
                        .findAny()
                        .orElse(null);
            }
        }
        return null;
    }
}
