package com.apollographql.federation.compatibility.model;

public class ProductDimension {
    private final String size;
    private final float weight;
    private final String unit;

    public ProductDimension(String size, float weight, String unit) {
        this.size = size;
        this.weight = weight;
        this.unit = unit;
    }

    public String getSize() {
        return size;
    }

    public float getWeight() {
        return weight;
    }

    public String getUnit() {
        return unit;
    }
}
