package com.refactorai.test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class AuditService {
    private List<AuditRecord> auditTrail = new ArrayList<>();

    public void recordAudit(String action, String userId, String details) {
        AuditRecord record = new AuditRecord();
        record.action = action;
        record.userId = userId;
        record.timestamp = new Date();
        record.details = details;
        record.ipAddress = "127.0.0.1";
        record.sessionId = UUID.randomUUID().toString();
        auditTrail.add(record);
    }

    public List<AuditRecord> getAuditTrail(String userId) {
        List<AuditRecord> result = new ArrayList<>();
        for (AuditRecord r : auditTrail) {
            if (userId.equals(r.userId)) {
                result.add(r);
            }
        }
        return result;
    }

    public List<AuditRecord> getRecentAuditRecords(int count) {
        int start = Math.max(0, auditTrail.size() - count);
        return new ArrayList<>(auditTrail.subList(start, auditTrail.size()));
    }

    public List<AuditRecord> getAuditTrail() {
        return auditTrail;
    }
}
