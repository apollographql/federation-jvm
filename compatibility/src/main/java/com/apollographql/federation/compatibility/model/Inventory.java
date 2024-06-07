package com.apollographql.federation.compatibility.model;

import java.util.List;

import static com.apollographql.federation.compatibility.model.DeprecatedProduct.DEPRECATED_PRODUCT;

public record Inventory(String id, List<DeprecatedProduct> deprecatedProducts) {

  public Inventory(String id) {
    this(id, List.of(DEPRECATED_PRODUCT));
  }

  public static Inventory resolveById(String id) {
    if ("apollo-oss".equals(id)) {
      return new Inventory(id);
    }
    return null;
  }
}
