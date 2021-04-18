package com.apollographql.federation.springexample.graphqljava;

import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

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

    public boolean isInStock() {
        return this.quantity > 0;
    }

    public static Product resolveReference(@NotNull Map<String, Object> reference) {
        if (!(reference.get("upc") instanceof String)) {
            return null;
        }
        final String upc = (String) reference.get("upc");
        try {
            // Why not?
            int quantity = Math.floorMod(
                    new BigInteger(1,
                            MessageDigest.getInstance("SHA1").digest(upc.getBytes())
                    ).intValue(),
                    10_000);

            return new Product(upc, quantity);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
