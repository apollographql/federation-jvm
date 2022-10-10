package com.apollographql.federation.graphqljava.exceptions;

import graphql.language.Directive;
import java.util.List;
import java.util.stream.Collectors;

/** Exception thrown when schema defines multiple `@link` directives importing federation spec. */
public class MultipleFederationLinksException extends RuntimeException {

  public MultipleFederationLinksException(List<Directive> directives) {
    super(
        "Schema imports multiple federation specs: "
            + directives.stream().map(Directive::toString).collect(Collectors.joining()));
  }
}
