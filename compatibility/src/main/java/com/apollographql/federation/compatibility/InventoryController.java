package com.apollographql.federation.compatibility;

import com.apollographql.federation.compatibility.model.Inventory;
import org.springframework.graphql.data.federation.EntityMapping;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.stereotype.Controller;

@Controller
public class InventoryController {
  @EntityMapping
  public Inventory inventory(@Argument("id") String id) {
    return Inventory.resolveById(id);
  }
}
