package com.apollographql.federation.compatibility.model;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record Product(String id, String sku, String pkg, ProductVariation variation, ProductDimension dimensions, User createdBy, List<ProductResearch> research) {

  private static final Map<String, Product> PRODUCTS = Stream.of(
    new Product("apollo-federation", "federation", "@apollo/federation", "OSS", List.of(ProductResearch.FEDERATION_STUDY)),
    new Product("apollo-studio", "studio", "", "platform", List.of(ProductResearch.STUDIO_STUDY))
  ).collect(Collectors.toMap(Product::id, product -> product));

  public Product(String id, String sku, String pkg, String variationId, List<ProductResearch> research) {
    this(
      id,
      sku,
      pkg,
      new ProductVariation(variationId),
      new ProductDimension("small", 1, "kg"),
      User.DEFAULT_USER, research
    );
  }

  public static Product resolveById(String id) {
    return PRODUCTS.get(id);
  }

  public static Product resolveBySkuAndPackage(String sku, String pkg) {
    for (Product product : PRODUCTS.values()) {
      if (product.sku().equals(sku) && product.pkg().equals(pkg)) {
        return product;
      }
    }
    return null;
  }

  public static Product resolveBySkuAndVariation(String sku, String variationId) {
    for (Product product : PRODUCTS.values()) {
      if (product.sku().equals(sku) && product.variation().id().equals(variationId)) {
        return product;
      }
    }
    return null;
  }
}
