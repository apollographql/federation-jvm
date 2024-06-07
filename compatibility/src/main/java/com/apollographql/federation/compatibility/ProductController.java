package com.apollographql.federation.compatibility;

import com.apollographql.federation.compatibility.model.Product;
import org.springframework.graphql.data.federation.EntityMapping;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class ProductController {
  @EntityMapping
  public Product product(
    @Argument String id,
    @Argument String sku,
    @Argument("package") String pkg,
    @Argument("variation") Map<String, String> variation
  ) {
    if (id != null) {
      return Product.resolveById(id);
    } else if (sku != null) {
      if (pkg != null) {
        return Product.resolveBySkuAndPackage(sku, pkg);
      } else if (variation != null) {
        return Product.resolveBySkuAndVariation(sku, variation.get("id"));
      }
    }
    return null;
  }

  @QueryMapping
  public Product product(@Argument String id) {
    return Product.resolveById(id);
  }

  @SchemaMapping(typeName = "Product", field = "package")
  public String getPackage(Product product) {
    return product.pkg();
  }
}
