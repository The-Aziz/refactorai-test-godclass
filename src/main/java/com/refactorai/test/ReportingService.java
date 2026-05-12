package com.refactorai.test;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class ReportingService {
    private UserService userService;
    private OrderService orderService;
    private ProductService productService;
    private AuditService auditService;
    private ConfigurationService configService;
    private LoggingService loggingService;

    public ReportingService(UserService userService, OrderService orderService, ProductService productService, AuditService auditService, ConfigurationService configService, LoggingService loggingService) {
        this.userService = userService;
        this.orderService = orderService;
        this.productService = productService;
        this.auditService = auditService;
        this.configService = configService;
        this.loggingService = loggingService;
    }

    public String generateUserReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== User Report ===\n");
        sb.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        if (userService != null) {
            sb.append("Total users: ").append(userService.getUserPasswords().size()).append("\n\n");
            for (String user : userService.getUserPasswords().keySet()) {
                Map<String, String> profile = userService.getUserProfile(user);
                sb.append("User: ").append(user).append("\n");
                sb.append("  Email: ").append(profile.get("email")).append("\n");
                sb.append("  Role: ").append(profile.get("role")).append("\n");
                sb.append("  Logins: ").append(profile.get("loginCount")).append("\n");
                sb.append("  Blocked: ").append(profile.get("blocked")).append("\n");
                sb.append("  Orders: ").append(profile.get("orderCount")).append("\n");
                sb.append("  Spending: $").append(String.format("%.2f", Double.parseDouble(profile.get("totalSpent")))).append("\n\n");
            }
        }
        return sb.toString();
    }

    public String generateOrderReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Order Report ===\n");
        if (orderService != null) {
            List<Order> orders = orderService.getAllOrders();
            sb.append("Total orders: ").append(orders.size()).append("\n");
            double totalRevenue = 0;
            int cancelled = 0;
            for (Order o : orders) {
                if ("CANCELLED".equals(o.status)) { cancelled++; }
                else { totalRevenue += o.totalAmount; }
            }
            sb.append("Revenue: $").append(String.format("%.2f", totalRevenue)).append("\n");
            sb.append("Cancelled: ").append(cancelled).append("\n");
        }
        return sb.toString();
    }

    public void exportReportToFile(String filename) {
        String report = generateUserReport();
        try {
            String outputDir = configService != null ? configService.getReportOutputDir() : "reports";
            File dir = new File(outputDir);
            if (!dir.exists()) dir.mkdirs();
            try (FileWriter fw = new FileWriter(new File(dir, filename))) { fw.write(report); }
            if (loggingService != null) loggingService.log("INFO", "Report exported to " + filename);
        } catch (IOException e) {
            if (loggingService != null) loggingService.log("ERROR", "Failed to export report: " + e.getMessage());
        }
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        if (userService != null) {
            stats.put("totalUsers", userService.getUserPasswords().size());
            stats.put("activeUsers", (int) userService.getUserBlocked().values().stream().filter(b -> !b).count());
            stats.put("blockedUsers", (int) userService.getUserBlocked().values().stream().filter(b -> b).count());
            stats.put("admins", (int) userService.getUserRoles().values().stream().filter(r -> "admin".equals(r)).count());
            stats.put("totalLogins", userService.getUserLoginCount().values().stream().mapToInt(Integer::intValue).sum());
        }
        if (orderService != null) {
            stats.put("totalOrders", orderService.getAllOrders().size());
            double revenue = 0;
            for (Order o : orderService.getAllOrders()) {
                if (!"CANCELLED".equals(o.status)) revenue += o.totalAmount;
            }
            stats.put("totalRevenue", revenue);
        }
        if (productService != null) {
            stats.put("totalProducts", productService.getAllProducts().size());
        }
        if (auditService != null) {
            stats.put("auditEntries", auditService.getAuditTrailSize());
        }
        return stats;
    }
}
