package com.refactorai.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnalyticsService {
    private OrderService orderService;
    private ProductService productService;

    public AnalyticsService(OrderService orderService, ProductService productService) {
        this.orderService = orderService;
        this.productService = productService;
    }

    public Map<String, Double> getRevenueByCategory() {
        Map<String, Double> revenue = new HashMap<>();
        for (Order o : orderService.getAllOrders()) {
            if (!"CANCELLED".equals(o.status)) {
                for (String itemName : o.items) {
                    for (Product p : productService.getAllProducts()) {
                        if (p.name.equals(itemName)) {
                            revenue.merge(p.category, p.price, Double::sum);
                        }
                    }
                }
            }
        }
        return revenue;
    }

    public List<String> getTopCustomers(int count) {
        Map<String, Double> spending = new HashMap<>();
        for (Order o : orderService.getAllOrders()) {
            if (!"CANCELLED".equals(o.status)) {
                spending.merge(o.userId, o.totalAmount, Double::sum);
            }
        }
        List<Map.Entry<String, Double>> entries = new ArrayList<>(spending.entrySet());
        entries.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<String> top = new ArrayList<>();
        for (int i = 0; i < Math.min(count, entries.size()); i++) {
            top.add(entries.get(i).getKey() + " ($" + String.format("%.2f", entries.get(i).getValue()) + ")");
        }
        return top;
    }

    public Map<String, Integer> getOrderStatusBreakdown() {
        Map<String, Integer> breakdown = new HashMap<>();
        for (Order o : orderService.getAllOrders()) {
            breakdown.merge(o.status, 1, Integer::sum);
        }
        return breakdown;
    }
}
