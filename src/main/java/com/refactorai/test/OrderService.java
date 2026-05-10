package com.refactorai.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OrderService {
    private List<Order> orders = new ArrayList<>();
    private final ProductService productService;
    private final LoggingService loggingService;
    private final AuditService auditService;

    public OrderService(ProductService productService, LoggingService loggingService, AuditService auditService) {
        this.productService = productService;
        this.loggingService = loggingService;
        this.auditService = auditService;
    }

    public void createOrder(String userId, List<String> productIds) {
        Order order = new Order();
        order.orderId = UUID.randomUUID().toString();
        order.userId = userId;
        order.status = "PENDING";
        double total = 0;
        for (String pid : productIds) {
            Product p = productService.findProduct(pid);
            if (p != null && p.available && p.stock > 0) {
                order.items.add(p.name);
                total += p.price;
                p.stock--;
            }
        }
        order.totalAmount = total;
        order.tax = total * 0.1;
        order.discount = total > 100 ? total * 0.05 : 0;
        order.totalAmount = total + order.tax - order.discount;
        orders.add(order);
        loggingService.log("INFO", "Order created: " + order.orderId + " for user " + userId + " total: " + order.totalAmount);
        auditService.recordAudit("CREATE_ORDER", userId, "Order " + order.orderId);
    }

    public void cancelOrder(String orderId) {
        for (Order order : orders) {
            if (order.orderId.equals(orderId)) {
                order.status = "CANCELLED";
                loggingService.log("INFO", "Order cancelled: " + orderId);
                // Restore stock
                for (String itemName : order.items) {
                    for (Product p : productService.getProducts()) {
                        if (p.name.equals(itemName)) {
                            p.stock++;
                        }
                    }
                }
                auditService.recordAudit("CANCEL_ORDER", order.userId, "Order " + orderId + " cancelled");
                return;
            }
        }
    }

    public List<Order> getUserOrders(String userId) {
        List<Order> result = new ArrayList<>();
        for (Order o : orders) {
            if (userId.equals(o.userId)) {
                result.add(o);
            }
        }
        return result;
    }

    public int countUserOrders(String username) {
        int count = 0;
        for (Order o : orders) {
            if (username.equals(o.userId)) count++;
        }
        return count;
    }

    public double calculateUserSpending(String username) {
        double total = 0;
        for (Order o : orders) {
            if (username.equals(o.userId) && !"CANCELLED".equals(o.status)) {
                total += o.totalAmount;
            }
        }
        return total;
    }

    public List<Order> getOrders() {
        return orders;
    }
}
