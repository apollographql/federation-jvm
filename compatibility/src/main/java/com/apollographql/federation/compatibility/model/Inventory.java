package com.apollographql.federation.compatibility.model;

import static com.apollographql.federation.compatibility.model.DeprecatedProduct.DEPRECATED_PRODUCT;

import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

public class Inventory {

    private final String id;
    private final List<DeprecatedProduct> deprecatedProducts;

    public Inventory(String id) {
        this.id = id;
        this.deprecatedProducts = List.of(DEPRECATED_PRODUCT);
    }

    public String getId() {
        return id;
    }

    public List<DeprecatedProduct> getDeprecatedProducts() {
        return deprecatedProducts;
    }

    public static Inventory resolveReference(@NotNull Map<String, Object> reference) {
        if (reference.get("id") instanceof String id && "apollo-oss".equals(id)) {
            return new Inventory(id);
        }

        return null;
    }
}
