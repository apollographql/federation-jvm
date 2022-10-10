package com.apollographql.federation.graphqljava.exceptions;

/** Exception thrown while processing link that specifies currently not supported version. */
public class UnsupportedFederationVersionException extends RuntimeException {
  public UnsupportedFederationVersionException(String federationSpec) {
    super("Specified federation spec = " + federationSpec + " is currently not supported");
  }
}
