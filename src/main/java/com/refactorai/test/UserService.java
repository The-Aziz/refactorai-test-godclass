package com.refactorai.test;

import java.text.SimpleDateFormat;
import java.util.*;

public class UserService {
    private Map<String, String> userPasswords = new HashMap<>();
    private Map<String, String> userEmails = new HashMap<>();
    private Map<String, String> userRoles = new HashMap<>();
    private Map<String, Date> userLastLogin = new HashMap<>();
    private Map<String, Integer> userLoginCount = new HashMap<>();
    private Map<String, Boolean> userBlocked = new HashMap<>();

    private final LoggingService loggingService;
    private final EmailService emailService;
    private final CacheService cacheService;
    private final AuditService auditService;
    private final OrderService orderService;

    public UserService(LoggingService loggingService, EmailService emailService, CacheService cacheService, AuditService auditService, OrderService orderService) {
        this.loggingService = loggingService;
        this.emailService = emailService;
        this.cacheService = cacheService;
        this.auditService = auditService;
        this.orderService = orderService;
    }

    public boolean createUser(String username, String password, String email) {
        if (username == null || username.isEmpty()) {
            loggingService.log("ERROR", "Cannot create user with empty username");
            return false;
        }
        if (userPasswords.containsKey(username)) {
            loggingService.log("WARN", "User already exists: " + username);
            return false;
        }
        if (!isValidEmail(email)) {
            loggingService.log("ERROR", "Invalid email: " + email);
            return false;
        }
        userPasswords.put(username, hashPassword(password));
        userEmails.put(username, email);
        userRoles.put(username, "user");
        userBlocked.put(username, false);
        userLoginCount.put(username, 0);
        loggingService.log("INFO", "User created: " + username);
        emailService.sendWelcomeEmail(username, email);
        cacheService.invalidateCache("users_list");
        auditService.recordAudit("CREATE_USER", username, "User account created");
        return true;
    }

    public boolean deleteUser(String username) {
        if (!userPasswords.containsKey(username)) {
            loggingService.log("WARN", "Cannot delete non-existent user: " + username);
            return false;
        }
        userPasswords.remove(username);
        userEmails.remove(username);
        userRoles.remove(username);
        userLastLogin.remove(username);
        userLoginCount.remove(username);
        userBlocked.remove(username);
        loggingService.log("INFO", "User deleted: " + username);
        cacheService.invalidateCache("users_list");
        auditService.recordAudit("DELETE_USER", username, "User account deleted");
        return true;
    }

    public boolean updateUserEmail(String username, String newEmail) {
        if (!userPasswords.containsKey(username)) return false;
        if (!isValidEmail(newEmail)) return false;
        String oldEmail = userEmails.get(username);
        userEmails.put(username, newEmail);
        loggingService.log("INFO", "Email updated for " + username + ": " + oldEmail + " -> " + newEmail);
        emailService.sendEmailChangeNotification(username, oldEmail, newEmail);
        auditService.recordAudit("UPDATE_EMAIL", username, oldEmail + " -> " + newEmail);
        return true;
    }

    public boolean updateUserRole(String username, String newRole) {
        if (!userPasswords.containsKey(username)) return false;
        if (!Arrays.asList("user", "admin", "moderator").contains(newRole)) return false;
        String oldRole = userRoles.get(username);
        userRoles.put(username, newRole);
        loggingService.log("INFO", "Role updated for " + username + " to " + newRole);
        auditService.recordAudit("UPDATE_ROLE", username, oldRole + " -> " + newRole);
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
        profile.put("orderCount", String.valueOf(orderService.countUserOrders(username)));
        profile.put("totalSpent", String.valueOf(orderService.calculateUserSpending(username)));
        return profile;
    }

    public List<Map<String, String>> listAllUsers() {
        String cacheKey = "users_list";
        Object cached = cacheService.getFromCache(cacheKey);
        if (cached != null) return (List<Map<String, String>>) cached;
        List<Map<String, String>> users = new ArrayList<>();
        for (String user : userPasswords.keySet()) {
            users.add(getUserProfile(user));
        }
        cacheService.putInCache(cacheKey, users);
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

    public String hashPassword(String password) {
        return String.valueOf(password.hashCode());
    }

    public boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }

    // Getters for maps (needed by AuthenticationService and ReportingService)
    public Map<String, String> getUserPasswords() { return userPasswords; }
    public Map<String, String> getUserEmails() { return userEmails; }
    public Map<String, String> getUserRoles() { return userRoles; }
    public Map<String, Date> getUserLastLogin() { return userLastLogin; }
    public Map<String, Integer> getUserLoginCount() { return userLoginCount; }
    public Map<String, Boolean> getUserBlocked() { return userBlocked; }
}
