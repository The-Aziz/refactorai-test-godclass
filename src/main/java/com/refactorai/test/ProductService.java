package com.refactorai.test;

import java.util.*;

public class ProductService {
    private List<Product> products = new ArrayList<>();
    private LoggingService loggingService;

    public ProductService(LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    public void addProduct(String id, String name, double price, int stock, String category) {
        Product p = new Product();
        p.productId = id;
        p.name = name;
        p.price = price;
        p.stock = stock;
        p.category = category;
        p.available = true;
        p.weight = 0;
        p.description = "";
        products.add(p);
        if (loggingService != null) loggingService.log("INFO", "Product added: " + name);
    }

    public Product findProduct(String productId) {
        for (Product p : products) {
            if (p.productId.equals(productId)) return p;
        }
        return null;
    }

    public List<Product> getProductsByCategory(String category) {
        List<Product> result = new ArrayList<>();
        for (Product p : products) {
            if (category.equals(p.category) && p.available) {
                result.add(p);
            }
        }
        return result;
    }

    public void updateProductStock(String productId, int newStock) {
        Product p = findProduct(productId);
        if (p != null) {
            p.stock = newStock;
            p.available = newStock > 0;
            if (loggingService != null) loggingService.log("INFO", "Stock updated for " + p.name + ": " + newStock);
        }
    }

    public List<Product> getAllProducts() {
        return products;
    }
}
