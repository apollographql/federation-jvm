package com.apollographql.federation.graphqljava.exceptions;

import graphql.language.Value;

/**
 * Exception thrown when processing invalid `@link` import definitions.
 *
 * <p>Unsupported imports:<br>
 * - specifying object import without specifying String name<br>
 * - specifying object rename that is not a String<br>
 * - attempting to import definition that is not a String nor an object definition<br>
 * - attempting to import
 */
public class UnsupportedLinkImportException extends RuntimeException {

  public UnsupportedLinkImportException(Value importedDefinition) {
    super("Unsupported import: " + importedDefinition);
  }

  public UnsupportedLinkImportException(String importedDefinition) {
    super(
        "New Federation feature " + importedDefinition + " imported using old Federation version");
  }
}
