package com.refactorai.test;

import java.util.*;

public class AuthenticationService {
    private Map<String, Integer> failedAttempts = new HashMap<>();
    private UserService userService;
    private LoggingService loggingService;
    private AuditService auditService;
    private ConfigurationService configService;
    private EmailService emailService;

    public AuthenticationService(UserService userService, LoggingService loggingService, AuditService auditService, ConfigurationService configService, EmailService emailService) {
        this.userService = userService;
        this.loggingService = loggingService;
        this.auditService = auditService;
        this.configService = configService;
        this.emailService = emailService;
    }

    public boolean authenticate(String username, String password) {
        if (userService == null || !userService.getUserPasswords().containsKey(username)) {
            if (loggingService != null) loggingService.log("WARN", "Auth attempt for non-existent user: " + username);
            if (auditService != null) auditService.recordAudit("AUTH_FAIL", username, "User not found");
            return false;
        }
        if (userService.isUserBlocked(username)) {
            if (loggingService != null) loggingService.log("WARN", "Auth attempt for blocked user: " + username);
            if (auditService != null) auditService.recordAudit("AUTH_BLOCKED", username, "Blocked user attempted login");
            return false;
        }
        int attempts = failedAttempts.getOrDefault(username, 0);
        int maxAttempts = configService != null ? configService.getMaxLoginAttempts() : 5;
        if (attempts >= maxAttempts) {
            userService.blockUser(username);
            if (loggingService != null) loggingService.log("WARN", "User locked due to too many failed attempts: " + username);
            if (auditService != null) auditService.recordAudit("AUTH_LOCKED", username, "Account locked after " + attempts + " attempts");
            return false;
        }
        String stored = userService.getUserPasswords().get(username);
        if (!stored.equals(userService.hashPassword(password))) {
            failedAttempts.put(username, attempts + 1);
            if (loggingService != null) loggingService.log("WARN", "Failed auth for " + username + " (attempt " + (attempts + 1) + ")");
            return false;
        }
        failedAttempts.put(username, 0);
        userService.getUserLastLogin().put(username, new Date());
        userService.getUserLoginCount().merge(username, 1, Integer::sum);
        if (loggingService != null) loggingService.log("INFO", "Successful login: " + username);
        if (auditService != null) auditService.recordAudit("AUTH_SUCCESS", username, "Login successful");
        return true;
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        if (!authenticate(username, oldPassword)) return false;
        if (newPassword.length() < 8) {
            if (loggingService != null) loggingService.log("WARN", "Password too short for " + username);
            return false;
        }
        userService.getUserPasswords().put(username, userService.hashPassword(newPassword));
        if (loggingService != null) loggingService.log("INFO", "Password changed for " + username);
        if (emailService != null) emailService.sendPasswordChangeNotification(username);
        if (auditService != null) auditService.recordAudit("CHANGE_PASSWORD", username, "Password changed");
        return true;
    }

    public Map<String, Integer> getFailedAttempts() {
        return failedAttempts;
    }
}
