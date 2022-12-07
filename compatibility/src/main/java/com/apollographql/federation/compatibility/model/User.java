package com.apollographql.federation.compatibility.model;

import java.util.Map;
import org.jetbrains.annotations.NotNull;

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

    public static User resolveReference(@NotNull Map<String, Object> reference) {
        if (reference.get("email") instanceof String email) {
            final User user = new User(email);
            if (reference.get("totalProductsCreated") instanceof Integer totalProductsCreated) {
                user.setTotalProductsCreated(totalProductsCreated);
            }
            if (reference.get("yearsOfEmployment") instanceof Integer yearsOfEmployment) {
                user.setYearsOfEmployment(yearsOfEmployment);
            }
            return user;
        }
        return null;
    }
}
