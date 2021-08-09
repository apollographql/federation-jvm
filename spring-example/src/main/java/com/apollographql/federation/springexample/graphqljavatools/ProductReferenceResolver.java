package com.apollographql.federation.springexample.graphqljavatools;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class ProductReferenceResolver {
  public static Product resolveReference(@NotNull Map<String, Object> reference) {
    if (!(reference.get("upc") instanceof String)) {
      return null;
    }
    final String upc = (String) reference.get("upc");
    try {
      // Why not?
      int quantity =
          Math.floorMod(
              new BigInteger(1, MessageDigest.getInstance("SHA1").digest(upc.getBytes()))
                  .intValue(),
              10_000);

      return new Product(upc, quantity);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
