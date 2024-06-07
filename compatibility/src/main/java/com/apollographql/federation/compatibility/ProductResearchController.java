package com.apollographql.federation.compatibility;

import com.apollographql.federation.compatibility.model.ProductResearch;
import org.springframework.graphql.data.federation.EntityMapping;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class ProductResearchController {
  @EntityMapping
  public ProductResearch productResearch(@Argument("study") Map<String, String> study) {
    return ProductResearch.resolveByCaseNumber(study.get("caseNumber"));
  }
}
