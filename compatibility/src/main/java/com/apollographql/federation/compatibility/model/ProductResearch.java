package com.apollographql.federation.compatibility.model;

import java.util.List;

public record ProductResearch(CaseStudy study, String outcome) {
  public static final ProductResearch FEDERATION_STUDY = new ProductResearch(new CaseStudy("1234", "Federation Study"));
  public static final ProductResearch STUDIO_STUDY = new ProductResearch(new CaseStudy("1235", "Studio Study"));
  public static final List<ProductResearch> RESEARCH_LIST = List.of(FEDERATION_STUDY, STUDIO_STUDY);

  public ProductResearch(CaseStudy study) {
    this(study, null);
  }

  public static ProductResearch resolveByCaseNumber(String caseNumber) {
    return RESEARCH_LIST.stream()
      .filter(research -> research.study.caseNumber().equals(caseNumber))
      .findAny()
      .orElse(null);
  }
}
