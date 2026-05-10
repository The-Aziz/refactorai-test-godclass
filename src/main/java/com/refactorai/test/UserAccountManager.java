package com.refactorai.test;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * UserAccountManager — a deliberate God Class that violates SRP.
 * It handles: user CRUD, authentication, email, file I/O, reporting,
 * caching, logging, configuration, order processing, audit trails,
 * product management, and analytics — all in one class.
 */
public class UserAccountManager {

    // ── User data fields ──
    private Map<String, String> userPasswords = new HashMap<>();
    private Map<String, String> userEmails = new HashMap<>();
    private Map<String, String> userRoles = new HashMap<>();
    private Map<String, Date> userLastLogin = new HashMap<>();
    private Map<String, Integer> userLoginCount = new HashMap<>();
    private Map<String, Boolean> userBlocked = new HashMap<>();

    // ── Cache fields ──
    private Map<String, Object> cache = new HashMap<>();
    private long cacheExpiry = 3600000;
    private Map<String, Long> cacheTimestamps = new HashMap<>();

    // ── Config fields ──
    private String smtpHost = "smtp.example.com";
    private int smtpPort = 587;
    private String adminEmail = "admin@example.com";
    private String logFilePath = "app.log";
    private String reportOutputDir = "reports";
    private int maxLoginAttempts = 5;
    private Map<String, Integer> failedAttempts = new HashMap<>();

    // ── Logging fields ──
    private List<String> logBuffer = new ArrayList<>();
    private boolean verboseLogging = true;

    // ── Orders (foreign data access) ──
    private List<Order> orders = new ArrayList<>();
    private List<Product> products = new ArrayList<>();
    private List<AuditRecord> auditTrail = new ArrayList<>();

    // ═══════════════════════════════════════════
    // User CRUD Operations
    // ═══════════════════════════════════════════

    public boolean createUser(String username, String password, String email) {
        if (username == null || username.isEmpty()) {
            log("ERROR", "Cannot create user with empty username");
            return false;
        }
        if (userPasswords.containsKey(username)) {
            log("WARN", "User already exists: " + username);
            return false;
        }
        if (!isValidEmail(email)) {
            log("ERROR", "Invalid email: " + email);
            return false;
        }
        userPasswords.put(username, hashPassword(password));
        userEmails.put(username, email);
        userRoles.put(username, "user");
        userBlocked.put(username, false);
        userLoginCount.put(username, 0);
        log("INFO", "User created: " + username);
        sendWelcomeEmail(username, email);
        invalidateCache("users_list");
        recordAudit("CREATE_USER", username, "User account created");
        return true;
    }

    public boolean deleteUser(String username) {
        if (!userPasswords.containsKey(username)) {
            log("WARN", "Cannot delete non-existent user: " + username);
            return false;
        }
        userPasswords.remove(username);
        userEmails.remove(username);
        userRoles.remove(username);
        userLastLogin.remove(username);
        userLoginCount.remove(username);
        userBlocked.remove(username);
        failedAttempts.remove(username);
        log("INFO", "User deleted: " + username);
        invalidateCache("users_list");
        recordAudit("DELETE_USER", username, "User account deleted");
        return true;
    }

    public boolean updateUserEmail(String username, String newEmail) {
        if (!userPasswords.containsKey(username)) return false;
        if (!isValidEmail(newEmail)) return false;
        String oldEmail = userEmails.get(username);
        userEmails.put(username, newEmail);
        log("INFO", "Email updated for " + username + ": " + oldEmail + " -> " + newEmail);
        sendEmailChangeNotification(username, oldEmail, newEmail);
        recordAudit("UPDATE_EMAIL", username, oldEmail + " -> " + newEmail);
        return true;
    }

    public boolean updateUserRole(String username, String newRole) {
        if (!userPasswords.containsKey(username)) return false;
        if (!Arrays.asList("user", "admin", "moderator").contains(newRole)) return false;
        String oldRole = userRoles.get(username);
        userRoles.put(username, newRole);
        log("INFO", "Role updated for " + username + " to " + newRole);
        recordAudit("UPDATE_ROLE", username, oldRole + " -> " + newRole);
        return true;
    }

    public Map<String, String> getUserProfile(String username) {
        if (!userPasswords.containsKey(username)) return null;
        Map<String, String> profile = new HashMap<>();
        profile.put("username", username);
        profile.put("email", userEmails.get(username));
        profile.put("role", userRoles.get(username));
        profile.put("loginCount", String.valueOf(userLoginCount.getOrDefault(username, 0)));
        Date lastLogin = userLastLogin.get(username);
        profile.put("lastLogin", lastLogin != null ? new SimpleDateFormat("yyyy-MM-dd HH:mm").format(lastLogin) : "never");
        profile.put("blocked", String.valueOf(userBlocked.getOrDefault(username, false)));
        profile.put("orderCount", String.valueOf(countUserOrders(username)));
        profile.put("totalSpent", String.valueOf(calculateUserSpending(username)));
        return profile;
    }

    public List<Map<String, String>> listAllUsers() {
        String cacheKey = "users_list";
        Object cached = getFromCache(cacheKey);
        if (cached != null) return (List<Map<String, String>>) cached;
        List<Map<String, String>> users = new ArrayList<>();
        for (String user : userPasswords.keySet()) {
            users.add(getUserProfile(user));
        }
        putInCache(cacheKey, users);
        return users;
    }

    public List<String> searchUsers(String query) {
        List<String> results = new ArrayList<>();
        for (String user : userPasswords.keySet()) {
            if (user.toLowerCase().contains(query.toLowerCase())) {
                results.add(user);
            }
            String email = userEmails.get(user);
            if (email != null && email.toLowerCase().contains(query.toLowerCase()) && !results.contains(user)) {
                results.add(user);
            }
        }
        return results;
    }

    // ═══════════════════════════════════════════
    // Authentication
    // ═══════════════════════════════════════════

    public boolean authenticate(String username, String password) {
        if (!userPasswords.containsKey(username)) {
            log("WARN", "Auth attempt for non-existent user: " + username);
            recordAudit("AUTH_FAIL", username, "User not found");
            return false;
        }
        if (userBlocked.getOrDefault(username, false)) {
            log("WARN", "Auth attempt for blocked user: " + username);
            recordAudit("AUTH_BLOCKED", username, "Blocked user attempted login");
            return false;
        }
        int attempts = failedAttempts.getOrDefault(username, 0);
        if (attempts >= maxLoginAttempts) {
            blockUser(username);
            log("WARN", "User locked due to too many failed attempts: " + username);
            recordAudit("AUTH_LOCKED", username, "Account locked after " + attempts + " attempts");
            return false;
        }
        String stored = userPasswords.get(username);
        if (!stored.equals(hashPassword(password))) {
            failedAttempts.put(username, attempts + 1);
            log("WARN", "Failed auth for " + username + " (attempt " + (attempts + 1) + ")");
            return false;
        }
        failedAttempts.put(username, 0);
        userLastLogin.put(username, new Date());
        userLoginCount.merge(username, 1, Integer::sum);
        log("INFO", "Successful login: " + username);
        recordAudit("AUTH_SUCCESS", username, "Login successful");
        return true;
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        if (!authenticate(username, oldPassword)) return false;
        if (newPassword.length() < 8) {
            log("WARN", "Password too short for " + username);
            return false;
        }
        userPasswords.put(username, hashPassword(newPassword));
        log("INFO", "Password changed for " + username);
        sendPasswordChangeNotification(username);
        recordAudit("CHANGE_PASSWORD", username, "Password changed");
        return true;
    }

    public boolean blockUser(String username) {
        if (!userPasswords.containsKey(username)) return false;
        userBlocked.put(username, true);
        log("INFO", "User blocked: " + username);
        recordAudit("BLOCK_USER", username, "Account blocked");
        return true;
    }

    public boolean unblockUser(String username) {
        if (!userPasswords.containsKey(username)) return false;
        userBlocked.put(username, false);
        failedAttempts.put(username, 0);
        log("INFO", "User unblocked: " + username);
        recordAudit("UNBLOCK_USER", username, "Account unblocked");
        return true;
    }

    public boolean isUserAdmin(String username) {
        return "admin".equals(userRoles.get(username));
    }

    public boolean isUserBlocked(String username) {
        return userBlocked.getOrDefault(username, false);
    }

    // ═══════════════════════════════════════════
    // Order Management (foreign data — Order class)
    // ═══════════════════════════════════════════

    public void createOrder(String userId, List<String> productIds) {
        Order order = new Order();
        order.orderId = UUID.randomUUID().toString();
        order.userId = userId;
        order.status = "PENDING";
        double total = 0;
        for (String pid : productIds) {
            Product p = findProduct(pid);
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
        log("INFO", "Order created: " + order.orderId + " for user " + userId + " total: " + order.totalAmount);
        recordAudit("CREATE_ORDER", userId, "Order " + order.orderId);
    }

    public void cancelOrder(String orderId) {
        for (Order order : orders) {
            if (order.orderId.equals(orderId)) {
                order.status = "CANCELLED";
                log("INFO", "Order cancelled: " + orderId);
                // Restore stock
                for (String itemName : order.items) {
                    for (Product p : products) {
                        if (p.name.equals(itemName)) {
                            p.stock++;
                        }
                    }
                }
                recordAudit("CANCEL_ORDER", order.userId, "Order " + orderId + " cancelled");
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

    private int countUserOrders(String username) {
        int count = 0;
        for (Order o : orders) {
            if (username.equals(o.userId)) count++;
        }
        return count;
    }

    private double calculateUserSpending(String username) {
        double total = 0;
        for (Order o : orders) {
            if (username.equals(o.userId) && !"CANCELLED".equals(o.status)) {
                total += o.totalAmount;
            }
        }
        return total;
    }

    // ═══════════════════════════════════════════
    // Product Management (foreign data — Product class)
    // ═══════════════════════════════════════════

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
        log("INFO", "Product added: " + name);
    }

    private Product findProduct(String productId) {
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
            log("INFO", "Stock updated for " + p.name + ": " + newStock);
        }
    }

    // ═══════════════════════════════════════════
    // Audit Trail (foreign data — AuditRecord class)
    // ═══════════════════════════════════════════

    private void recordAudit(String action, String userId, String details) {
        AuditRecord record = new AuditRecord();
        record.action = action;
        record.userId = userId;
        record.timestamp = new Date();
        record.details = details;
        record.ipAddress = "127.0.0.1";
        record.sessionId = UUID.randomUUID().toString();
        auditTrail.add(record);
    }

    public List<AuditRecord> getAuditTrail(String userId) {
        List<AuditRecord> result = new ArrayList<>();
        for (AuditRecord r : auditTrail) {
            if (userId.equals(r.userId)) {
                result.add(r);
            }
        }
        return result;
    }

    public List<AuditRecord> getRecentAuditRecords(int count) {
        int start = Math.max(0, auditTrail.size() - count);
        return new ArrayList<>(auditTrail.subList(start, auditTrail.size()));
    }

    // ═══════════════════════════════════════════
    // Email (should be its own service)
    // ═══════════════════════════════════════════

    private void sendWelcomeEmail(String username, String email) {
        String subject = "Welcome to Our Platform, " + username + "!";
        String body = "Dear " + username + ",\n\nThank you for registering.\n\nBest regards,\nThe Team";
        sendEmail(email, subject, body);
    }

    private void sendEmailChangeNotification(String username, String oldEmail, String newEmail) {
        sendEmail(oldEmail, "Email Changed", "Your email was changed to " + newEmail);
        sendEmail(newEmail, "Email Confirmed", "Welcome to your new email, " + username);
    }

    private void sendPasswordChangeNotification(String username) {
        String email = userEmails.get(username);
        if (email != null) {
            sendEmail(email, "Password Changed", "Your password was recently changed.");
        }
    }

    private void sendEmail(String to, String subject, String body) {
        log("INFO", "Sending email to " + to + ": " + subject);
        try { Thread.sleep(10); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        log("INFO", "Email sent to " + to);
    }

    // ═══════════════════════════════════════════
    // Reporting (should be its own service)
    // ═══════════════════════════════════════════

    public String generateUserReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== User Report ===\n");
        sb.append("Generated: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).append("\n");
        sb.append("Total users: ").append(userPasswords.size()).append("\n\n");
        for (String user : userPasswords.keySet()) {
            sb.append("User: ").append(user).append("\n");
            sb.append("  Email: ").append(userEmails.get(user)).append("\n");
            sb.append("  Role: ").append(userRoles.get(user)).append("\n");
            sb.append("  Logins: ").append(userLoginCount.getOrDefault(user, 0)).append("\n");
            sb.append("  Blocked: ").append(userBlocked.getOrDefault(user, false)).append("\n");
            sb.append("  Orders: ").append(countUserOrders(user)).append("\n");
            sb.append("  Spending: $").append(String.format("%.2f", calculateUserSpending(user))).append("\n\n");
        }
        return sb.toString();
    }

    public String generateOrderReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Order Report ===\n");
        sb.append("Total orders: ").append(orders.size()).append("\n");
        double totalRevenue = 0;
        int cancelled = 0;
        for (Order o : orders) {
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
            File dir = new File(reportOutputDir);
            if (!dir.exists()) dir.mkdirs();
            try (FileWriter fw = new FileWriter(new File(dir, filename))) { fw.write(report); }
            log("INFO", "Report exported to " + filename);
        } catch (IOException e) {
            log("ERROR", "Failed to export report: " + e.getMessage());
        }
    }

    public Map<String, Object> getDashboardStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userPasswords.size());
        stats.put("activeUsers", (int) userBlocked.values().stream().filter(b -> !b).count());
        stats.put("blockedUsers", (int) userBlocked.values().stream().filter(b -> b).count());
        stats.put("admins", (int) userRoles.values().stream().filter(r -> "admin".equals(r)).count());
        stats.put("totalLogins", userLoginCount.values().stream().mapToInt(Integer::intValue).sum());
        stats.put("totalOrders", orders.size());
        stats.put("totalProducts", products.size());
        stats.put("auditEntries", auditTrail.size());
        double revenue = 0;
        for (Order o : orders) {
            if (!"CANCELLED".equals(o.status)) revenue += o.totalAmount;
        }
        stats.put("totalRevenue", revenue);
        return stats;
    }

    // ═══════════════════════════════════════════
    // Caching (should be its own service)
    // ═══════════════════════════════════════════

    private Object getFromCache(String key) {
        if (!cache.containsKey(key)) return null;
        Long ts = cacheTimestamps.get(key);
        if (ts == null || System.currentTimeMillis() - ts > cacheExpiry) {
            cache.remove(key); cacheTimestamps.remove(key); return null;
        }
        return cache.get(key);
    }

    private void putInCache(String key, Object value) {
        cache.put(key, value); cacheTimestamps.put(key, System.currentTimeMillis());
    }

    private void invalidateCache(String key) { cache.remove(key); cacheTimestamps.remove(key); }

    public void clearAllCache() { cache.clear(); cacheTimestamps.clear(); log("INFO", "Cache cleared"); }

    // ═══════════════════════════════════════════
    // Logging (should be its own service)
    // ═══════════════════════════════════════════

    private void log(String level, String message) {
        String entry = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " [" + level + "] " + message;
        logBuffer.add(entry);
        if (verboseLogging) System.out.println(entry);
    }

    public void flushLogs() {
        try (FileWriter fw = new FileWriter(logFilePath, true)) {
            for (String entry : logBuffer) fw.write(entry + "\n");
            logBuffer.clear();
        } catch (IOException e) { System.err.println("Log flush failed: " + e.getMessage()); }
    }

    public List<String> getRecentLogs(int count) {
        int start = Math.max(0, logBuffer.size() - count);
        return new ArrayList<>(logBuffer.subList(start, logBuffer.size()));
    }

    // ═══════════════════════════════════════════
    // Configuration (should be its own service)
    // ═══════════════════════════════════════════

    public void loadConfig(String configFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) applyConfigProperty(parts[0].trim(), parts[1].trim());
            }
            log("INFO", "Config loaded from " + configFile);
        } catch (IOException e) { log("ERROR", "Config load failed: " + e.getMessage()); }
    }

    private void applyConfigProperty(String key, String value) {
        switch (key) {
            case "smtp.host": smtpHost = value; break;
            case "smtp.port": smtpPort = Integer.parseInt(value); break;
            case "admin.email": adminEmail = value; break;
            case "log.file": logFilePath = value; break;
            case "report.dir": reportOutputDir = value; break;
            case "max.login.attempts": maxLoginAttempts = Integer.parseInt(value); break;
            case "cache.expiry": cacheExpiry = Long.parseLong(value); break;
            case "verbose.logging": verboseLogging = Boolean.parseBoolean(value); break;
        }
    }

    public Map<String, String> getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("smtp.host", smtpHost);
        config.put("smtp.port", String.valueOf(smtpPort));
        config.put("admin.email", adminEmail);
        config.put("log.file", logFilePath);
        config.put("report.dir", reportOutputDir);
        config.put("max.login.attempts", String.valueOf(maxLoginAttempts));
        config.put("cache.expiry", String.valueOf(cacheExpiry));
        config.put("verbose.logging", String.valueOf(verboseLogging));
        return config;
    }

    // ═══════════════════════════════════════════
    // Analytics (should be its own service)
    // ═══════════════════════════════════════════

    public Map<String, Double> getRevenueByCategory() {
        Map<String, Double> revenue = new HashMap<>();
        for (Order o : orders) {
            if (!"CANCELLED".equals(o.status)) {
                for (String itemName : o.items) {
                    for (Product p : products) {
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
        for (Order o : orders) {
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
        for (Order o : orders) {
            breakdown.merge(o.status, 1, Integer::sum);
        }
        return breakdown;
    }

    // ═══════════════════════════════════════════
    // Utility
    // ═══════════════════════════════════════════

    private String hashPassword(String password) {
        return String.valueOf(password.hashCode());
    }

    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }
}
