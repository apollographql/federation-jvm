package com.apollographql.federation.graphqljava.data;

public class ProductDimension {
  private final String size;
  private final float weight;

  public ProductDimension(String size, float weight) {
    this.size = size;
    this.weight = weight;
  }

  public String getSize() {
    return size;
  }

  public float getWeight() {
    return weight;
  }
}
