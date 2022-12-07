package com.apollographql.federation.compatibility.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public class Product {

    private static final Map<String, Product> PRODUCTS = Stream.of(
            new Product("apollo-federation", "federation", "@apollo/federation", "OSS", List.of(ProductResearch.FEDERATION_STUDY)),
            new Product("apollo-studio", "studio", "", "platform", List.of(ProductResearch.STUDIO_STUDY))
    ).collect(Collectors.toMap(Product::getId, product -> product));

    private final String id;
    private final String sku;
    private final String pkg;
    private final ProductVariation variation;
    private final ProductDimension dimensions;
    private final User createdBy;

    private final List<ProductResearch> research;

    public Product(String id) {
        this.id = id;
        this.sku = "";
        this.pkg = "";
        this.variation = new ProductVariation("");
        this.dimensions = new ProductDimension("small", 1, "kg");
        this.createdBy = User.DEFAULT_USER;
        this.research = new ArrayList<>();
    }

    public Product(String id, String sku, String pkg, String variationId, List<ProductResearch> research) {
        this.id = id;
        this.sku = sku;
        this.pkg = pkg;
        this.variation = new ProductVariation(variationId);
        this.dimensions = new ProductDimension("small", 1, "kg");
        this.createdBy = User.DEFAULT_USER;
        this.research = research;
    }

    public Product(String sku, String pkg) {
        this.id = "";
        this.sku = sku;
        this.pkg = pkg;
        this.variation = new ProductVariation("");
        this.dimensions = new ProductDimension("small", 1, "kg");
        this.createdBy = User.DEFAULT_USER;
        this.research = new ArrayList<>();
    }

    public Product(String sku, ProductVariation variation) {
        this.id = "";
        this.pkg = "";
        this.sku = sku;
        this.variation = variation;
        this.dimensions = new ProductDimension("small", 1, "kg");
        this.createdBy = User.DEFAULT_USER;
        this.research = new ArrayList<>();
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

    public String getPkg() {
        return pkg;
    }

    public ProductVariation getVariation() {
        return variation;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public List<ProductResearch> getResearch() {
        return research;
    }

    public static Product resolveById(String id) {
        return PRODUCTS.get(id);
    }

    public static Product resolveReference(@NotNull Map<String, Object> reference) {
        if (reference.get("id") instanceof String productId) {
            return PRODUCTS.get(productId);
        } else {
            String productSku = (String) reference.get("sku");
            if (reference.get("package") instanceof String productPackage) {
                for (Product product : PRODUCTS.values()) {
                    if (product.getSku().equals(productSku) && product.getPkg().equals(productPackage)) {
                        return product;
                    }
                }
            } else if (reference.get("variation") instanceof HashMap productVariation) {
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
