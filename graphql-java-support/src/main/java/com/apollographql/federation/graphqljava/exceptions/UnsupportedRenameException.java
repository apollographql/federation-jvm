package com.apollographql.federation.graphqljava.exceptions;

/** Exception thrown when attempting to rename directive imports that cannot be renamed. */
public class UnsupportedRenameException extends RuntimeException {

  public UnsupportedRenameException(String name) {
    super("Current version of Apollo Federation does not allow renaming " + name + " directive.");
  }
}
