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

  public UnsupportedLinkImportException(String importedDefinition, int minVersion, int version) {
    super(
        String.format(
            "Federation v%.1f feature %s imported using old Federation v%.1f version",
            minVersion / 10.0, importedDefinition, version / 10.0));
  }
}
