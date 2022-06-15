package com.apollographql.federation.graphqljava.data;

import java.util.Objects;

@SuppressWarnings("WeakerAccess")
public class Product {
  public static final Product PLANCK = new Product("PLANCK", "P", "Planck", 180);

  private final String upc;
  private final String sku;
  private final String name;
  private final int price;

  public Product(final String upc, final String sku, final String name, final int price) {
    this.upc = upc;
    this.sku = sku;
    this.name = name;
    this.price = price;
  }

  public String getUpc() {
    return this.upc;
  }

  public String getSku() {
    return this.sku;
  }

  public String getName() {
    return this.name;
  }

  public int getPrice() {
    return this.price;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) return true;
    if (!(o instanceof Product)) return false;
    final Product other = (Product) o;
    final Object this$upc = this.getUpc();
    final Object other$upc = other.getUpc();
    if (this$upc == null ? other$upc != null : !this$upc.equals(other$upc)) return false;
    final Object this$sku = this.getSku();
    final Object other$sku = other.getSku();
    if (this$sku == null ? other$sku != null : !this$sku.equals(other$sku)) return false;
    final Object this$name = this.getName();
    final Object other$name = other.getName();
    if (this$name == null ? other$name != null : !this$name.equals(other$name)) return false;
    return this.getPrice() == other.getPrice();
  }

  @Override
  public int hashCode() {
    return Objects.hash(upc, sku, name, price);
  }

  @Override
  public String toString() {
    return "Product(upc="
        + this.getUpc()
        + ", sku="
        + this.getSku()
        + ", name="
        + this.getName()
        + ", price="
        + this.getPrice()
        + ")";
  }
}
