package com.apollographql.federation.compatibility.model;

public class User {

  public static User DEFAULT_USER = new User("support@apollographql.com");

  private final String email;
  private final String name;
  private Integer totalProductsCreated;

  private int yearsOfEmployment;

  public User(String email) {
    this.email = email;
    this.totalProductsCreated = 1337;
    this.name = "Jane Smith";
  }

  public String getEmail() {
    return email;
  }

  public String getName() {
    return name;
  }

  public Integer getTotalProductsCreated() {
    return totalProductsCreated;
  }

  public void setTotalProductsCreated(Integer totalProductsCreated) {
    this.totalProductsCreated = totalProductsCreated;
  }

  public int getYearsOfEmployment() {
    return yearsOfEmployment;
  }

  public void setYearsOfEmployment(int yearsOfEmployment) {
    this.yearsOfEmployment = yearsOfEmployment;
  }
}
