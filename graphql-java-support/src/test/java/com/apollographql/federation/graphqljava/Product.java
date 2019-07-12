package com.apollographql.federation.graphqljava;

import lombok.Data;

@Data
class Product {
    static final Product PLANCK = new Product("PLANCK", "P", "Planck", 180);

    private final String upc;
    private final String sku;
    private final String name;
    private final int price;
}
