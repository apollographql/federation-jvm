package com.apollographql.federation.inventory;

public class Product {
    private final String upc;
    private final int shippingEstimate;
    private int weight;
    private int price;

    public Product(String upc, int shippingEstimate) {
        this.upc = upc;
        this.shippingEstimate = shippingEstimate;
    }

    public String getUpc() {
        return upc;
    }

    public int getShippingEstimate() {
        return shippingEstimate;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(final int weight) {
        this.weight = weight;
    }

    public int getPrice() {
        return price;
    }

    public void setPrice(final int price) {
        this.price = price;
    }

    public boolean isInStock() {
        return this.shippingEstimate > 0;
    }
}
