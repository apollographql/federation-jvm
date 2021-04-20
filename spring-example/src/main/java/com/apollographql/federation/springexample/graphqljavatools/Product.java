package com.apollographql.federation.springexample.graphqljavatools;

public class Product {
    private final String upc;
    private final int quantity;

    public Product(String upc, int quantity) {
        this.upc = upc;
        this.quantity = quantity;
    }

    public String getUpc() {
        return upc;
    }

    public int getQuantity() {
        return quantity;
    }
}
