package com.apollographql.federation.user;

import java.util.Objects;

public class User {

    private final String id;
    private String name;
    private String username;
    private Address address;

    public User(final String id) {
        this.id = id;
    }

    public User(final String id, final String username) {
        this.id = id;
        this.username = username;
    }

    public User(final String id, final String name, final String username) {
        this.id = id;
        this.name = name;
        this.username = username;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(final Address address) {
        this.address = address;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final User user = (User) o;
        return Objects.equals(id, user.id) &&
                Objects.equals(name, user.name) &&
                Objects.equals(username, user.username) &&
                Objects.equals(address, user.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, username, address);
    }
}
