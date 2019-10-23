package com.apollographql.federation.review;

import java.util.List;

public class Product {

    private String upc;
    private List<Review> reviews;

    public Product(final String upc, final List<Review> reviews) {
        this.upc = upc;
        this.reviews = reviews;
    }

    public Product(final String upc) {
        this.upc = upc;
    }

    public Product() {
    }

    public String getUpc() {
        return upc;
    }

    public void setUpc(final String upc) {
        this.upc = upc;
    }

    public List<Review> getReviews() {
        return reviews;
    }

    public void setReviews(final List<Review> reviews) {
        this.reviews = reviews;
    }
}
