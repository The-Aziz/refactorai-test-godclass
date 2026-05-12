package com.refactorai.test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class ReportingService {
    private UserService userService;
    private OrderService orderService;
    private ProductService productService;
    private AuditService auditService;
    private LoggingService loggingService;
    private ConfigurationService configService;

    public ReportingService(UserService userService, OrderService orderService, ProductService productService,
                            AuditService auditService, LoggingService loggingService, ConfigurationService configService) {
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
        sb.append("Total users: ").append(userService.getUserCount()).append("\n\n");
        for (String user : userService.getAllUsernames()) {
            Map<String, String> profile = userService.getUserProfile(user);
            sb.append("User: ").append(user).append("\n");
            sb.append("  Email: ").append(profile.get("email")).append("\n");
            sb.append("  Role: ").append(profile.get("role")).append("\n");
            sb.append("  Logins: ").append(profile.get("loginCount")).append("\n");
            sb.append("  Blocked: ").append(profile.get("blocked")).append("\n");
            sb.append("  Orders: ").append(profile.get("orderCount")).append("\n");
            sb.append("  Spending: $").append(String.format("%.2f", Double.parseDouble(profile.get("totalSpent")))).append("\n\n");
        }
        return sb.toString();
    }

    public String generateOrderReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Order Report ===\n");
        sb.append("Total orders: ").append(orderService.getOrderCount()).append("\n");
        double totalRevenue = 0;
        int cancelled = 0;
        for (Order o : orderService.getAllOrders()) {
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
            String reportOutputDir = configService != null ? configService.getReportOutputDir() : "reports";
            File dir = new File(reportOutputDir);
            if (!dir.exists()) dir.mkdirs();
            try (FileWriter fw = new FileWriter(new File(dir, filename))) { fw.write(report); }
            if (loggingService != null) loggingService.log("INFO", "Report exported to " + filename);
        } catch (IOException e) {
            if (loggingService != null) loggingService.log("ERROR", "Failed to export report: " + e.getMessage());
        }
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userService.getUserCount());
        stats.put("activeUsers", (int) userService.getBlockedStatuses().stream().filter(b -> !b).count());
        stats.put("blockedUsers", (int) userService.getBlockedStatuses().stream().filter(b -> b).count());
        stats.put("admins", (int) userService.getUserRoles().stream().filter(r -> "admin".equals(r)).count());
        stats.put("totalLogins", userService.getLoginCounts().stream().mapToInt(Integer::intValue).sum());
        stats.put("totalOrders", orderService.getOrderCount());
        stats.put("totalProducts", productService.getProductCount());
        stats.put("auditEntries", auditService.getAuditTrailSize());
        double revenue = 0;
        for (Order o : orderService.getAllOrders()) {
            if (!"CANCELLED".equals(o.status)) revenue += o.totalAmount;
        }
        stats.put("totalRevenue", revenue);
        return stats;
    }
}
