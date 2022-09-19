package com.apollographql.federation.graphqljava.exceptions;

import graphql.language.Value;

/**
 * Exception thrown when processing invalid `@link` import definitions.
 *
 * Unsupported imports:
 * - specifying object import without specifying String name
 * - specifying object rename that is not a String
 * - attempting to import definition that is not a String nor an object definition
 */
public class UnsupportedLinkImportException extends RuntimeException {

  public UnsupportedLinkImportException(Value importedDefinition) {
    super("Unsupported import: " + importedDefinition);
  }
}
