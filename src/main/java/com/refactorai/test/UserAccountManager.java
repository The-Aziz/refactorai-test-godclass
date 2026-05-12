package com.refactorai.test;

import java.util.*;

/**
 * UserAccountManager — a deliberate God Class that violates SRP.
 * Now refactored to delegate to specialized services.
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
        this.configService = new ConfigurationService();
        this.auditService = new AuditService();

        // Circular dependency handled via setters
        this.loggingService.setConfigurationService(this.configService);
        this.configService.setLoggingService(this.loggingService);

        this.cacheService = new CacheService(this.configService, this.loggingService);
        this.productService = new ProductService(this.loggingService);
        this.orderService = new OrderService(this.productService, this.loggingService, this.auditService);
        this.emailService = new EmailService(this.loggingService);
        this.userService = new UserService(this.loggingService, this.emailService, this.cacheService, this.auditService, this.orderService);
        this.emailService.setUserService(this.userService);

        this.authService = new AuthenticationService(this.userService, this.loggingService, this.auditService, this.configService, this.emailService);
        this.analyticsService = new AnalyticsService(this.orderService, this.productService);
        this.reportingService = new ReportingService(this.userService, this.orderService, this.productService, this.auditService, this.configService, this.loggingService);
    }

    // ═══════════════════════════════════════════
    // User CRUD Operations
    // ═══════════════════════════════════════════

    public boolean createUser(String username, String password, String email) {
        return userService.createUser(username, password, email);
    }

    public boolean deleteUser(String username) {
        return userService.deleteUser(username, authService);
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
        return userService.blockUser(username);
    }

    public boolean unblockUser(String username) {
        return userService.unblockUser(username, authService);
    }

    public boolean isUserAdmin(String username) {
        return userService.isUserAdmin(username);
    }

    public boolean isUserBlocked(String username) {
        return userService.isUserBlocked(username);
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

    // ═══════════════════════════════════════════
    // Product Management
    // ═══════════════════════════════════════════

    public void addProduct(String id, String name, double price, int stock, String category) {
        productService.addProduct(id, name, price, stock, category);
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

    public List<AuditRecord> getAuditTrail(String userId) {
        return auditService.getAuditTrail(userId);
    }

    public List<AuditRecord> getRecentAuditRecords(int count) {
        return auditService.getRecentAuditRecords(count);
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

    public void clearAllCache() {
        cacheService.clearAllCache();
    }

    // ═══════════════════════════════════════════
    // Logging
    // ═══════════════════════════════════════════

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
}
