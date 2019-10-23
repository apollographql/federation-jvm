package com.apollographql.federation.review;

import java.util.List;

public class User {

    private final String id;
    private String username;
    private List<Review> reviews;

    public User(final String id) {
        this.id = id;
    }

    public User(final String id, final String username) {
        this.id = id;
        this.username = username;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public List<Review> getReviews() {
        return reviews;
    }

    public void setReviews(final List<Review> reviews) {
        this.reviews = reviews;
    }
}
