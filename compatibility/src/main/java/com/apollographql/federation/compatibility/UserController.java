package com.apollographql.federation.compatibility;

import com.apollographql.federation.compatibility.model.User;
import org.springframework.graphql.data.federation.EntityMapping;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class UserController {

  @EntityMapping
  public User user(@Argument String email, @Argument Integer totalProductsCreated, @Argument Integer yearsOfEmployment) {
    final User user = new User(email);
    if (totalProductsCreated != null) {
      user.setTotalProductsCreated(totalProductsCreated);
    }
    if (yearsOfEmployment != null) {
      user.setYearsOfEmployment(yearsOfEmployment);
    }
    return user;
  }

  @SchemaMapping(typeName = "User", field = "averageProductsCreatedPerYear")
  public Integer getAverageProductsCreatedPerYear(User user) {
    if (user.getTotalProductsCreated() != null && user.getYearsOfEmployment() > 0) {
      return Math.round(1.0f * user.getTotalProductsCreated() / user.getYearsOfEmployment());
    } else {
      return null;
    }
  }
}
