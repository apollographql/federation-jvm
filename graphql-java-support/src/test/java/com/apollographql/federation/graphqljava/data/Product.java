package com.apollographql.federation.graphqljava.data;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class Product {

  private static final Map<String, Product> PRODUCTS = Stream.of(
    new Product("apollo-federation", "federation", "@apollo/federation", "OSS"),
    new Product("apollo-studio", "studio", "", "platform")
  ).collect(Collectors.toMap(Product::getId, product -> product));

  private final String id;
  private final String sku;
  private final String productPackage;
  private final ProductVariation variation;
  private final ProductDimension dimensions;
  private final User createdBy;

  public Product(String id) {
    this.id = id;
    this.sku = "";
    this.productPackage = "";
    this.variation = new ProductVariation("");
    this.dimensions = new ProductDimension("small", 1);

    this.createdBy = new User("support@apollographql.com");
  }

  public Product(String id, String sku, String productPackage, String variationId) {
    this.id = id;
    this.sku = sku;
    this.productPackage = productPackage;
    this.variation = new ProductVariation(variationId);
    this.dimensions = new ProductDimension("small", 1);
    this.createdBy = new User("support@apollographql.com");
  }

  public Product(String sku, String productPackage) {
    this.id = "";
    this.sku = sku;
    this.productPackage = productPackage;
    this.variation = new ProductVariation("");
    this.dimensions = new ProductDimension("small", 1);
    this.createdBy = new User("support@apollographql.com");
  }

  public Product(String sku, ProductVariation variation) {
    this.id = "";
    this.productPackage = "";
    this.sku = sku;
    this.variation = variation;
    this.dimensions = new ProductDimension("small", 1);
    this.createdBy = new User("support@apollographql.com");
  }

  public String getId() {
    return id;
  }

  public String getSku() {
    return sku;
  }

  public ProductDimension getDimensions() {
    return dimensions;
  }

  public String getProductPackage() {
    return productPackage;
  }

  public ProductVariation getVariation() {
    return variation;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public static Product resolveById(String id) {
    return PRODUCTS.get(id);
  }

  public static Product resolveReference(@NotNull Map<String, Object> reference) {
    if (reference.get("id") instanceof String) {
      final String productId = (String) reference.get("id");
      return PRODUCTS.get(productId);
    } else {
      String productSku = (String) reference.get("sku");
      if (reference.get("package") instanceof String) {
        final String productPackage = (String) reference.get("package");
        for (Product product : PRODUCTS.values()) {
          if (product.getSku().equals(productSku) && product.getProductPackage().equals(productPackage)) {
            return product;
          }
        }
      } else if (reference.get("variation") instanceof HashMap) {
        final HashMap productVariation = (HashMap) reference.get("variation");
        for (Product product : PRODUCTS.values()) {
          if (product.getSku().equals(productSku) && product.getVariation().getId().equals(productVariation.get("id"))) {
            return product;
          }
        }
      }
    }

    return null;
  }
}
