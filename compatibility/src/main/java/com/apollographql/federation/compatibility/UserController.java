package com.apollographql.federation.compatibility;

import com.apollographql.federation.compatibility.model.User;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class UserController {

    @SchemaMapping(typeName="User", field="averageProductsCreatedPerYear")
    public Integer getAverageProductsCreatedPerYear(User user) {
        if (user.getTotalProductsCreated() != null) {
            return Math.round(1.0f * user.getTotalProductsCreated() / user.getYearsOfEmployment());
        } else {
            return null;
        }
    }
}