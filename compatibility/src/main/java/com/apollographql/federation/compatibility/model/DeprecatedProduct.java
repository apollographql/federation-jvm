package com.apollographql.federation.compatibility.model;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class DeprecatedProduct {

    public static DeprecatedProduct DEPRECATED_PRODUCT = new DeprecatedProduct("apollo-federation-v1", "@apollo/federation-v1", "Migrate to Federation V2");

    private final String sku;
    private final String pkg;
    private final String reason;
    private final User createdBy;

    public DeprecatedProduct(String sku, String pkg) {
        this.sku = sku;
        this.pkg = pkg;
        this.reason = null;
        this.createdBy = User.DEFAULT_USER;
    }

    public DeprecatedProduct(String sku, String pkg, String reason) {
        this.sku = sku;
        this.pkg = pkg;
        this.reason = reason;
        this.createdBy = User.DEFAULT_USER;
    }

    public DeprecatedProduct(String sku, String pkg, String reason, User createdBy) {
        this.sku = sku;
        this.pkg = pkg;
        this.reason = reason;
        this.createdBy = createdBy;
    }

    public String getSku() {
        return sku;
    }

    public String getPkg() {
        return pkg;
    }

    public String getReason() {
        return reason;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public static DeprecatedProduct resolveBySkuAndPackage(String sku, String pkg) {
        if (DEPRECATED_PRODUCT.sku.equals(sku) && DEPRECATED_PRODUCT.pkg.equals(pkg))  {
            return DEPRECATED_PRODUCT;
        } else {
            return null;
        }
    }

    public static DeprecatedProduct resolveReference(@NotNull Map<String, Object> reference) {
        if (reference.get("sku") instanceof String sku && reference.get("package") instanceof String pkg) {
            return resolveBySkuAndPackage(sku, pkg);
        }
        return null;
    }
}
