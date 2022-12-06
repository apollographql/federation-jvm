package com.apollographql.federation.compatibility;

import com.apollographql.federation.compatibility.model.DeprecatedProduct;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

@Controller
public class DeprecatedProductController {

    @QueryMapping
    public DeprecatedProduct deprecatedProduct(@Argument String sku, @Argument("package") String pkg) {
        return DeprecatedProduct.resolveBySkuAndPackage(sku, pkg);
    }

    @SchemaMapping(typeName="DeprecatedProduct", field="package")
    public String getPackage(DeprecatedProduct product) {
        return product.getPkg();
    }
}
