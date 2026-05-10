package com.refactorai.test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class AuthenticationService {
    private Map<String, Integer> failedAttempts = new HashMap<>();

    private final UserService userService;
    private final LoggingService loggingService;
    private final AuditService auditService;
    private final EmailService emailService;
    private final ConfigurationService configService;

    public AuthenticationService(UserService userService, LoggingService loggingService, AuditService auditService, EmailService emailService, ConfigurationService configService) {
        this.userService = userService;
        this.loggingService = loggingService;
        this.auditService = auditService;
        this.emailService = emailService;
        this.configService = configService;
    }

    public boolean authenticate(String username, String password) {
        if (!userService.getUserPasswords().containsKey(username)) {
            loggingService.log("WARN", "Auth attempt for non-existent user: " + username);
            auditService.recordAudit("AUTH_FAIL", username, "User not found");
            return false;
        }
        if (userService.getUserBlocked().getOrDefault(username, false)) {
            loggingService.log("WARN", "Auth attempt for blocked user: " + username);
            auditService.recordAudit("AUTH_BLOCKED", username, "Blocked user attempted login");
            return false;
        }
        int attempts = failedAttempts.getOrDefault(username, 0);
        if (attempts >= configService.getMaxLoginAttempts()) {
            blockUser(username);
            loggingService.log("WARN", "User locked due to too many failed attempts: " + username);
            auditService.recordAudit("AUTH_LOCKED", username, "Account locked after " + attempts + " attempts");
            return false;
        }
        String stored = userService.getUserPasswords().get(username);
        if (!stored.equals(userService.hashPassword(password))) {
            failedAttempts.put(username, attempts + 1);
            loggingService.log("WARN", "Failed auth for " + username + " (attempt " + (attempts + 1) + ")");
            return false;
        }
        failedAttempts.put(username, 0);
        userService.getUserLastLogin().put(username, new Date());
        userService.getUserLoginCount().merge(username, 1, Integer::sum);
        loggingService.log("INFO", "Successful login: " + username);
        auditService.recordAudit("AUTH_SUCCESS", username, "Login successful");
        return true;
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        if (!authenticate(username, oldPassword)) return false;
        if (newPassword.length() < 8) {
            loggingService.log("WARN", "Password too short for " + username);
            return false;
        }
        userService.getUserPasswords().put(username, userService.hashPassword(newPassword));
        loggingService.log("INFO", "Password changed for " + username);
        emailService.sendPasswordChangeNotification(username, userService.getUserEmails().get(username));
        auditService.recordAudit("CHANGE_PASSWORD", username, "Password changed");
        return true;
    }

    public boolean blockUser(String username) {
        if (!userService.getUserPasswords().containsKey(username)) return false;
        userService.getUserBlocked().put(username, true);
        loggingService.log("INFO", "User blocked: " + username);
        auditService.recordAudit("BLOCK_USER", username, "Account blocked");
        return true;
    }

    public boolean unblockUser(String username) {
        if (!userService.getUserPasswords().containsKey(username)) return false;
        userService.getUserBlocked().put(username, false);
        failedAttempts.put(username, 0);
        loggingService.log("INFO", "User unblocked: " + username);
        auditService.recordAudit("UNBLOCK_USER", username, "Account unblocked");
        return true;
    }

    public void clearFailedAttempts(String username) {
        failedAttempts.remove(username);
    }

    public boolean isUserAdmin(String username) {
        return "admin".equals(userService.getUserRoles().get(username));
    }

    public boolean isUserBlocked(String username) {
        return userService.getUserBlocked().getOrDefault(username, false);
    }

    public Map<String, Integer> getFailedAttempts() {
        return failedAttempts;
    }
}
