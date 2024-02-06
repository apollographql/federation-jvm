package com.apollographql.federation.compatibility;

import com.apollographql.federation.compatibility.model.DeprecatedProduct;
import com.apollographql.federation.compatibility.model.Product;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

// TEST
@Controller
public class ProductController {

    @QueryMapping
    public Product product(@Argument String id) {
        return Product.resolveById(id);
    }

    @SchemaMapping(typeName="Product", field="package")
    public String getPackage(Product product) {
        return product.getPkg();
    }
}
