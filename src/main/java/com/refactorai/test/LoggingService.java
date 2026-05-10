package com.refactorai.test;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class LoggingService {
    private List<String> logBuffer = new ArrayList<>();
    private boolean verboseLogging = true;
    private String logFilePath = "app.log";

    public void log(String level, String message) {
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

    public void setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
    }

    public boolean isVerboseLogging() {
        return verboseLogging;
    }

    public void setLogFilePath(String logFilePath) {
        this.logFilePath = logFilePath;
    }

    public String getLogFilePath() {
        return logFilePath;
    }
}
