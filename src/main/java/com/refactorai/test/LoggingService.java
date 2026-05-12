package com.refactorai.test;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class LoggingService {
    private List<String> logBuffer = new ArrayList<>();
    private ConfigurationService configService;

    public void setConfigurationService(ConfigurationService configService) {
        this.configService = configService;
    }

    public void log(String level, String message) {
        String entry = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " [" + level + "] " + message;
        logBuffer.add(entry);
        if (configService != null && configService.isVerboseLogging()) {
            System.out.println(entry);
        }
    }

    public void flushLogs() {
        String logFilePath = configService != null ? configService.getLogFilePath() : "app.log";
        try (FileWriter fw = new FileWriter(logFilePath, true)) {
            for (String entry : logBuffer) fw.write(entry + "\n");
            logBuffer.clear();
        } catch (IOException e) { System.err.println("Log flush failed: " + e.getMessage()); }
    }

    public List<String> getRecentLogs(int count) {
        int start = Math.max(0, logBuffer.size() - count);
        return new ArrayList<>(logBuffer.subList(start, logBuffer.size()));
    }
}
