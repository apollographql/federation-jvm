package com.apollographql.federation.graphqljava.exceptions;

/**
 * Exception thrown when GraphQL object type does not specify all @keys specified on its interface.
 */
public class MissingKeyException extends RuntimeException {

  public MissingKeyException(String objectType, String interfaceType) {
    super(
        String.format(
            "Object %s does not specify @key fields specified by its interface %s",
            objectType, interfaceType));
  }
}
