package com.refactorai.test;

import java.io.*;
import java.net.*;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * UserAccountManager — a deliberate God Class that violates SRP.
 * It handles: user CRUD, authentication, email, file I/O, reporting,
 * caching, logging, and configuration — all in one class.
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
        return true;
    }

    public boolean updateUserEmail(String username, String newEmail) {
        if (!userPasswords.containsKey(username)) return false;
        if (!isValidEmail(newEmail)) return false;
        String oldEmail = userEmails.get(username);
        userEmails.put(username, newEmail);
        log("INFO", "Email updated for " + username + ": " + oldEmail + " -> " + newEmail);
        sendEmailChangeNotification(username, oldEmail, newEmail);
        return true;
    }

    public boolean updateUserRole(String username, String newRole) {
        if (!userPasswords.containsKey(username)) return false;
        if (!Arrays.asList("user", "admin", "moderator").contains(newRole)) return false;
        userRoles.put(username, newRole);
        log("INFO", "Role updated for " + username + " to " + newRole);
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

    // ═══════════════════════════════════════════
    // Authentication
    // ═══════════════════════════════════════════

    public boolean authenticate(String username, String password) {
        if (!userPasswords.containsKey(username)) {
            log("WARN", "Auth attempt for non-existent user: " + username);
            return false;
        }
        if (userBlocked.getOrDefault(username, false)) {
            log("WARN", "Auth attempt for blocked user: " + username);
            return false;
        }
        int attempts = failedAttempts.getOrDefault(username, 0);
        if (attempts >= maxLoginAttempts) {
            blockUser(username);
            log("WARN", "User locked due to too many failed attempts: " + username);
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
        return true;
    }

    public boolean blockUser(String username) {
        if (!userPasswords.containsKey(username)) return false;
        userBlocked.put(username, true);
        log("INFO", "User blocked: " + username);
        return true;
    }

    public boolean unblockUser(String username) {
        if (!userPasswords.containsKey(username)) return false;
        userBlocked.put(username, false);
        failedAttempts.put(username, 0);
        log("INFO", "User unblocked: " + username);
        return true;
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
            sendEmail(email, "Password Changed", "Your password was recently changed. If this wasn't you, contact support.");
        }
    }

    private void sendEmail(String to, String subject, String body) {
        log("INFO", "Sending email to " + to + ": " + subject);
        // Simulated email sending
        try {
            Thread.sleep(10); // simulate network delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log("INFO", "Email sent successfully to " + to);
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
            sb.append("\n");
        }
        return sb.toString();
    }

    public void exportReportToFile(String filename) {
        String report = generateUserReport();
        try {
            File dir = new File(reportOutputDir);
            if (!dir.exists()) dir.mkdirs();
            try (FileWriter fw = new FileWriter(new File(dir, filename))) {
                fw.write(report);
            }
            log("INFO", "Report exported to " + filename);
        } catch (IOException e) {
            log("ERROR", "Failed to export report: " + e.getMessage());
        }
    }

    public Map<String, Integer> getUserStatistics() {
        Map<String, Integer> stats = new HashMap<>();
        stats.put("totalUsers", userPasswords.size());
        stats.put("activeUsers", (int) userBlocked.values().stream().filter(b -> !b).count());
        stats.put("blockedUsers", (int) userBlocked.values().stream().filter(b -> b).count());
        stats.put("admins", (int) userRoles.values().stream().filter(r -> "admin".equals(r)).count());
        stats.put("totalLogins", userLoginCount.values().stream().mapToInt(Integer::intValue).sum());
        return stats;
    }

    // ═══════════════════════════════════════════
    // Caching (should be its own service)
    // ═══════════════════════════════════════════

    private Object getFromCache(String key) {
        if (!cache.containsKey(key)) return null;
        Long timestamp = cacheTimestamps.get(key);
        if (timestamp == null || System.currentTimeMillis() - timestamp > cacheExpiry) {
            cache.remove(key);
            cacheTimestamps.remove(key);
            return null;
        }
        return cache.get(key);
    }

    private void putInCache(String key, Object value) {
        cache.put(key, value);
        cacheTimestamps.put(key, System.currentTimeMillis());
    }

    private void invalidateCache(String key) {
        cache.remove(key);
        cacheTimestamps.remove(key);
    }

    public void clearAllCache() {
        cache.clear();
        cacheTimestamps.clear();
        log("INFO", "All cache cleared");
    }

    // ═══════════════════════════════════════════
    // Logging (should be its own service)
    // ═══════════════════════════════════════════

    private void log(String level, String message) {
        String entry = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " [" + level + "] " + message;
        logBuffer.add(entry);
        if (verboseLogging) {
            System.out.println(entry);
        }
    }

    public void flushLogs() {
        try (FileWriter fw = new FileWriter(logFilePath, true)) {
            for (String entry : logBuffer) {
                fw.write(entry + "\n");
            }
            logBuffer.clear();
        } catch (IOException e) {
            System.err.println("Failed to flush logs: " + e.getMessage());
        }
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
                if (parts.length == 2) {
                    applyConfigProperty(parts[0].trim(), parts[1].trim());
                }
            }
            log("INFO", "Configuration loaded from " + configFile);
        } catch (IOException e) {
            log("ERROR", "Failed to load config: " + e.getMessage());
        }
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
    // Utility (private helpers)
    // ═══════════════════════════════════════════

    private String hashPassword(String password) {
        // Simplified hash for demo
        return String.valueOf(password.hashCode());
    }

    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }
}
