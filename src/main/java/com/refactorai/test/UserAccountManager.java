package com.refactorai.test;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * UserAccountManager — a deliberate God Class that now delegates to focused services.
 */
public class UserAccountManager {

    private final LoggingService loggingService;
    private final ConfigurationService configService;
    private final AuditService auditService;
    private final CacheService cacheService;
    private final EmailService emailService;
    private final ProductService productService;
    private final OrderService orderService;
    private final UserService userService;
    private final AuthenticationService authService;
    private final AnalyticsService analyticsService;
    private final ReportingService reportingService;

    public UserAccountManager() {
        this.loggingService = new LoggingService();
        this.configService = new ConfigurationService(loggingService);
        this.auditService = new AuditService();
        this.cacheService = new CacheService(configService, loggingService);
        this.emailService = new EmailService(loggingService);
        this.productService = new ProductService(loggingService);
        this.orderService = new OrderService(productService, loggingService, auditService);
        this.userService = new UserService(loggingService, emailService, cacheService, auditService, orderService);
        this.authService = new AuthenticationService(userService, loggingService, auditService, emailService, configService);
        this.analyticsService = new AnalyticsService(orderService, productService);
        this.reportingService = new ReportingService(userService, orderService, productService, auditService, loggingService, configService);
    }

    // ═══════════════════════════════════════════
    // User CRUD Operations
    // ═══════════════════════════════════════════

    public boolean createUser(String username, String password, String email) {
        return userService.createUser(username, password, email);
    }

    public boolean deleteUser(String username) {
        boolean deleted = userService.deleteUser(username);
        if (deleted) {
            authService.clearFailedAttempts(username);
        }
        return deleted;
    }

    public boolean updateUserEmail(String username, String newEmail) {
        return userService.updateUserEmail(username, newEmail);
    }

    public boolean updateUserRole(String username, String newRole) {
        return userService.updateUserRole(username, newRole);
    }

    public Map<String, String> getUserProfile(String username) {
        return userService.getUserProfile(username);
    }

    public List<Map<String, String>> listAllUsers() {
        return userService.listAllUsers();
    }

    public List<String> searchUsers(String query) {
        return userService.searchUsers(query);
    }

    // ═══════════════════════════════════════════
    // Authentication
    // ═══════════════════════════════════════════

    public boolean authenticate(String username, String password) {
        return authService.authenticate(username, password);
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        return authService.changePassword(username, oldPassword, newPassword);
    }

    public boolean blockUser(String username) {
        return authService.blockUser(username);
    }

    public boolean unblockUser(String username) {
        return authService.unblockUser(username);
    }

    public boolean isUserAdmin(String username) {
        return authService.isUserAdmin(username);
    }

    public boolean isUserBlocked(String username) {
        return authService.isUserBlocked(username);
    }

    // ═══════════════════════════════════════════
    // Order Management
    // ═══════════════════════════════════════════

    public void createOrder(String userId, List<String> productIds) {
        orderService.createOrder(userId, productIds);
    }

    public void cancelOrder(String orderId) {
        orderService.cancelOrder(orderId);
    }

    public List<Order> getUserOrders(String userId) {
        return orderService.getUserOrders(userId);
    }

    private int countUserOrders(String username) {
        return orderService.countUserOrders(username);
    }

    private double calculateUserSpending(String username) {
        return orderService.calculateUserSpending(username);
    }

    // ═══════════════════════════════════════════
    // Product Management
    // ═══════════════════════════════════════════

    public void addProduct(String id, String name, double price, int stock, String category) {
        productService.addProduct(id, name, price, stock, category);
    }

    private Product findProduct(String productId) {
        return productService.findProduct(productId);
    }

    public List<Product> getProductsByCategory(String category) {
        return productService.getProductsByCategory(category);
    }

    public void updateProductStock(String productId, int newStock) {
        productService.updateProductStock(productId, newStock);
    }

    // ═══════════════════════════════════════════
    // Audit Trail
    // ═══════════════════════════════════════════

    private void recordAudit(String action, String userId, String details) {
        auditService.recordAudit(action, userId, details);
    }

    public List<AuditRecord> getAuditTrail(String userId) {
        return auditService.getAuditTrail(userId);
    }

    public List<AuditRecord> getRecentAuditRecords(int count) {
        return auditService.getRecentAuditRecords(count);
    }

    // ═══════════════════════════════════════════
    // Email
    // ═══════════════════════════════════════════

    private void sendWelcomeEmail(String username, String email) {
        emailService.sendWelcomeEmail(username, email);
    }

    private void sendEmailChangeNotification(String username, String oldEmail, String newEmail) {
        emailService.sendEmailChangeNotification(username, oldEmail, newEmail);
    }

    private void sendPasswordChangeNotification(String username) {
        emailService.sendPasswordChangeNotification(username, userService.getUserEmails().get(username));
    }

    private void sendEmail(String to, String subject, String body) {
        emailService.sendEmail(to, subject, body);
    }

    // ═══════════════════════════════════════════
    // Reporting
    // ═══════════════════════════════════════════

    public String generateUserReport() {
        return reportingService.generateUserReport();
    }

    public String generateOrderReport() {
        return reportingService.generateOrderReport();
    }

    public void exportReportToFile(String filename) {
        reportingService.exportReportToFile(filename);
    }

    public Map<String, Object> getDashboardStats() {
        return reportingService.getDashboardStats();
    }

    // ═══════════════════════════════════════════
    // Caching
    // ═══════════════════════════════════════════

    private Object getFromCache(String key) {
        return cacheService.getFromCache(key);
    }

    private void putInCache(String key, Object value) {
        cacheService.putInCache(key, value);
    }

    private void invalidateCache(String key) {
        cacheService.invalidateCache(key);
    }

    public void clearAllCache() {
        cacheService.clearAllCache();
    }

    // ═══════════════════════════════════════════
    // Logging
    // ═══════════════════════════════════════════

    private void log(String level, String message) {
        loggingService.log(level, message);
    }

    public void flushLogs() {
        loggingService.flushLogs();
    }

    public List<String> getRecentLogs(int count) {
        return loggingService.getRecentLogs(count);
    }

    // ═══════════════════════════════════════════
    // Configuration
    // ═══════════════════════════════════════════

    public void loadConfig(String configFile) {
        configService.loadConfig(configFile);
    }

    public Map<String, String> getConfig() {
        return configService.getConfig();
    }

    // ═══════════════════════════════════════════
    // Analytics
    // ═══════════════════════════════════════════

    public Map<String, Double> getRevenueByCategory() {
        return analyticsService.getRevenueByCategory();
    }

    public List<String> getTopCustomers(int count) {
        return analyticsService.getTopCustomers(count);
    }

    public Map<String, Integer> getOrderStatusBreakdown() {
        return analyticsService.getOrderStatusBreakdown();
    }

    // ═══════════════════════════════════════════
    // Utility
    // ═══════════════════════════════════════════

    private String hashPassword(String password) {
        return userService.hashPassword(password);
    }

    private boolean isValidEmail(String email) {
        return userService.isValidEmail(email);
    }
}
