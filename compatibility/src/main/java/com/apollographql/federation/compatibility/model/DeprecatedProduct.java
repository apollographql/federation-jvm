package com.apollographql.federation.compatibility.model;

public record DeprecatedProduct(String sku, String pkg, String reason, User createdBy) {

  public static DeprecatedProduct DEPRECATED_PRODUCT = new DeprecatedProduct("apollo-federation-v1", "@apollo/federation-v1", "Migrate to Federation V2");

  public DeprecatedProduct(String sku, String pkg, String reason) {
    this(sku, pkg, reason, User.DEFAULT_USER);
  }

  public static DeprecatedProduct resolveBySkuAndPackage(String sku, String pkg) {
    if (DEPRECATED_PRODUCT.sku.equals(sku) && DEPRECATED_PRODUCT.pkg.equals(pkg)) {
      return DEPRECATED_PRODUCT;
    } else {
      return null;
    }
  }
}
