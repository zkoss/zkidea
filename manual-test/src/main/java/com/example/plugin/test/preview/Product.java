package com.example.plugin.test.preview;

/**
 * Sample bean backing the grid/listbox preview cases under
 * {@code src/main/webapp/preview/cases/}. Multiple property types (String, double,
 * int, boolean) so the fixtures can exercise columns, converters and selection.
 */
public class Product {

    private int id;
    private String name;
    private String category;
    private double price;
    private int qty;
    private boolean inStock;

    public Product() {
    }

    public Product(int id, String name, String category, double price, int qty, boolean inStock) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.price = price;
        this.qty = qty;
        this.inStock = inStock;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }

    public boolean isInStock() { return inStock; }
    public void setInStock(boolean inStock) { this.inStock = inStock; }

    @Override
    public String toString() {
        return name;
    }
}
