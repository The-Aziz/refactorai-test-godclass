package com.refactorai.test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ReportingService {
    private final UserService userService;
    private final OrderService orderService;
    private final ProductService productService;
    private final AuditService auditService;
    private final LoggingService loggingService;
    private final ConfigurationService configService;

    public ReportingService(UserService userService, OrderService orderService, ProductService productService, AuditService auditService, LoggingService loggingService, ConfigurationService configService) {
        this.userService = userService;
        this.orderService = orderService;
        this.productService = productService;
        this.auditService = auditService;
        this.loggingService = loggingService;
        this.configService = configService;
    }

    public String generateUserReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== User Report ===\n");
        sb.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        sb.append("Total users: ").append(userService.getUserPasswords().size()).append("\n\n");
        for (String user : userService.getUserPasswords().keySet()) {
            sb.append("User: ").append(user).append("\n");
            sb.append("  Email: ").append(userService.getUserEmails().get(user)).append("\n");
            sb.append("  Role: ").append(userService.getUserRoles().get(user)).append("\n");
            sb.append("  Logins: ").append(userService.getUserLoginCount().getOrDefault(user, 0)).append("\n");
            sb.append("  Blocked: ").append(userService.getUserBlocked().getOrDefault(user, false)).append("\n");
            sb.append("  Orders: ").append(orderService.countUserOrders(user)).append("\n");
            sb.append("  Spending: $").append(String.format("%.2f", orderService.calculateUserSpending(user))).append("\n\n");
        }
        return sb.toString();
    }

    public String generateOrderReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Order Report ===\n");
        sb.append("Total orders: ").append(orderService.getOrders().size()).append("\n");
        double totalRevenue = 0;
        int cancelled = 0;
        for (Order o : orderService.getOrders()) {
            if ("CANCELLED".equals(o.status)) { cancelled++; }
            else { totalRevenue += o.totalAmount; }
        }
        sb.append("Revenue: $").append(String.format("%.2f", totalRevenue)).append("\n");
        sb.append("Cancelled: ").append(cancelled).append("\n");
        return sb.toString();
    }

    public void exportReportToFile(String filename) {
        String report = generateUserReport();
        try {
            File dir = new File(configService.getReportOutputDir());
            if (!dir.exists()) dir.mkdirs();
            try (FileWriter fw = new FileWriter(new File(dir, filename))) { fw.write(report); }
            loggingService.log("INFO", "Report exported to " + filename);
        } catch (IOException e) {
            loggingService.log("ERROR", "Failed to export report: " + e.getMessage());
        }
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userService.getUserPasswords().size());
        stats.put("activeUsers", (int) userService.getUserBlocked().values().stream().filter(b -> !b).count());
        stats.put("blockedUsers", (int) userService.getUserBlocked().values().stream().filter(b -> b).count());
        stats.put("admins", (int) userService.getUserRoles().values().stream().filter(r -> "admin".equals(r)).count());
        stats.put("totalLogins", userService.getUserLoginCount().values().stream().mapToInt(Integer::intValue).sum());
        stats.put("totalOrders", orderService.getOrders().size());
        stats.put("totalProducts", productService.getProducts().size());
        stats.put("auditEntries", auditService.getAuditTrail().size());
        double revenue = 0;
        for (Order o : orderService.getOrders()) {
            if (!"CANCELLED".equals(o.status)) revenue += o.totalAmount;
        }
        stats.put("totalRevenue", revenue);
        return stats;
    }
}
