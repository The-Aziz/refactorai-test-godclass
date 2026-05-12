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

    private LoggingService loggingService;
    private AuditService auditService;
    private CacheService cacheService;
    private EmailService emailService;
    private OrderService orderService;
    private AuthenticationService authService;

    public UserService(LoggingService loggingService, AuditService auditService, CacheService cacheService,
                       EmailService emailService, OrderService orderService) {
        this.loggingService = loggingService;
        this.auditService = auditService;
        this.cacheService = cacheService;
        this.emailService = emailService;
        this.orderService = orderService;
    }

    public void setAuthenticationService(AuthenticationService authService) {
        this.authService = authService;
    }

    public boolean createUser(String username, String password, String email) {
        if (username == null || username.isEmpty()) {
            if (loggingService != null) loggingService.log("ERROR", "Cannot create user with empty username");
            return false;
        }
        if (userPasswords.containsKey(username)) {
            if (loggingService != null) loggingService.log("WARN", "User already exists: " + username);
            return false;
        }
        if (!isValidEmail(email)) {
            if (loggingService != null) loggingService.log("ERROR", "Invalid email: " + email);
            return false;
        }
        userPasswords.put(username, authService.hashPassword(password));
        userEmails.put(username, email);
        userRoles.put(username, "user");
        userBlocked.put(username, false);
        userLoginCount.put(username, 0);
        if (loggingService != null) loggingService.log("INFO", "User created: " + username);
        if (emailService != null) emailService.sendWelcomeEmail(username, email);
        if (cacheService != null) cacheService.invalidateCache("users_list");
        if (auditService != null) auditService.recordAudit("CREATE_USER", username, "User account created");
        return true;
    }

    public boolean deleteUser(String username) {
        if (!userPasswords.containsKey(username)) {
            if (loggingService != null) loggingService.log("WARN", "Cannot delete non-existent user: " + username);
            return false;
        }
        userPasswords.remove(username);
        userEmails.remove(username);
        userRoles.remove(username);
        userLastLogin.remove(username);
        userLoginCount.remove(username);
        userBlocked.remove(username);
        if (authService != null) authService.getFailedAttempts().remove(username);
        if (loggingService != null) loggingService.log("INFO", "User deleted: " + username);
        if (cacheService != null) cacheService.invalidateCache("users_list");
        if (auditService != null) auditService.recordAudit("DELETE_USER", username, "User account deleted");
        return true;
    }

    public boolean updateUserEmail(String username, String newEmail) {
        if (!userPasswords.containsKey(username)) return false;
        if (!isValidEmail(newEmail)) return false;
        String oldEmail = userEmails.get(username);
        userEmails.put(username, newEmail);
        if (loggingService != null) loggingService.log("INFO", "Email updated for " + username + ": " + oldEmail + " -> " + newEmail);
        if (emailService != null) emailService.sendEmailChangeNotification(username, oldEmail, newEmail);
        if (auditService != null) auditService.recordAudit("UPDATE_EMAIL", username, oldEmail + " -> " + newEmail);
        return true;
    }

    public boolean updateUserRole(String username, String newRole) {
        if (!userPasswords.containsKey(username)) return false;
        if (!Arrays.asList("user", "admin", "moderator").contains(newRole)) return false;
        String oldRole = userRoles.get(username);
        userRoles.put(username, newRole);
        if (loggingService != null) loggingService.log("INFO", "Role updated for " + username + " to " + newRole);
        if (auditService != null) auditService.recordAudit("UPDATE_ROLE", username, oldRole + " -> " + newRole);
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
        Object cached = cacheService != null ? cacheService.getFromCache(cacheKey) : null;
        if (cached != null) return (List<Map<String, String>>) cached;
        List<Map<String, String>> users = new ArrayList<>();
        for (String user : userPasswords.keySet()) {
            users.add(getUserProfile(user));
        }
        if (cacheService != null) cacheService.putInCache(cacheKey, users);
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

    public boolean blockUser(String username) {
        if (!userPasswords.containsKey(username)) return false;
        userBlocked.put(username, true);
        if (loggingService != null) loggingService.log("INFO", "User blocked: " + username);
        if (auditService != null) auditService.recordAudit("BLOCK_USER", username, "Account blocked");
        return true;
    }

    public boolean unblockUser(String username) {
        if (!userPasswords.containsKey(username)) return false;
        userBlocked.put(username, false);
        if (authService != null) authService.getFailedAttempts().put(username, 0);
        if (loggingService != null) loggingService.log("INFO", "User unblocked: " + username);
        if (auditService != null) auditService.recordAudit("UNBLOCK_USER", username, "Account unblocked");
        return true;
    }

    public boolean isUserAdmin(String username) {
        return "admin".equals(userRoles.get(username));
    }

    public boolean isUserBlocked(String username) {
        return userBlocked.getOrDefault(username, false);
    }

    public String getUserEmail(String username) {
        return userEmails.get(username);
    }

    public boolean userExists(String username) {
        return userPasswords.containsKey(username);
    }

    public String getStoredPassword(String username) {
        return userPasswords.get(username);
    }

    public void setStoredPassword(String username, String hashedPassword) {
        userPasswords.put(username, hashedPassword);
    }

    public void recordLogin(String username) {
        userLastLogin.put(username, new Date());
        userLoginCount.merge(username, 1, Integer::sum);
    }

    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }

    public int getUserCount() {
        return userPasswords.size();
    }

    public Collection<Boolean> getBlockedStatuses() {
        return userBlocked.values();
    }

    public Collection<String> getUserRoles() {
        return userRoles.values();
    }

    public Collection<Integer> getLoginCounts() {
        return userLoginCount.values();
    }

    public Set<String> getAllUsernames() {
        return userPasswords.keySet();
    }
}
