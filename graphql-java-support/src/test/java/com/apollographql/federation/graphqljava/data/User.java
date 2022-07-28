package com.apollographql.federation.graphqljava.data;

public class User {
  private final String email;
  private final Integer totalProductsCreated;
  private final String name;

  public User(String email) {
    this.email = email;
    this.totalProductsCreated = 1337;
    this.name = "Jane Smith";
  }

  public String getEmail() {
    return email;
  }

  public Integer getTotalProductsCreated() {
    return totalProductsCreated;
  }

  public String getName() {
    return name;
  }
}
