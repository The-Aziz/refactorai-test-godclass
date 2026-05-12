package com.refactorai.test;

import java.io.*;
import java.util.*;

public class ConfigurationService {
    private String smtpHost = "smtp.example.com";
    private int smtpPort = 587;
    private String adminEmail = "admin@example.com";
    private String logFilePath = "app.log";
    private String reportOutputDir = "reports";
    private int maxLoginAttempts = 5;
    private long cacheExpiry = 3600000;
    private boolean verboseLogging = true;

    private LoggingService loggingService;

    public void setLoggingService(LoggingService loggingService) {
        this.loggingService = loggingService;
    }

    public void loadConfig(String configFile) {
        try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) applyConfigProperty(parts[0].trim(), parts[1].trim());
            }
            if (loggingService != null) loggingService.log("INFO", "Config loaded from " + configFile);
        } catch (IOException e) {
            if (loggingService != null) loggingService.log("ERROR", "Config load failed: " + e.getMessage());
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

    public String getSmtpHost() { return smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public String getAdminEmail() { return adminEmail; }
    public String getLogFilePath() { return logFilePath; }
    public String getReportOutputDir() { return reportOutputDir; }
    public int getMaxLoginAttempts() { return maxLoginAttempts; }
    public long getCacheExpiry() { return cacheExpiry; }
    public boolean isVerboseLogging() { return verboseLogging; }
}
