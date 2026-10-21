package com.conference.model;

import java.io.Serializable;
import java.time.Instant;

/** Append-only event stored in the same transaction as its business change. */
public final class AuditEvent implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String timestamp;
    private final String action;
    private final String userId;
    private final String paperId;
    private final String details;
    public AuditEvent(String action, String userId, String paperId, String details) {
        this.timestamp = Instant.now().toString();
        this.action = action;
        this.userId = userId;
        this.paperId = paperId;
        this.details = details;
    }
    public String getAction() { return action; }
    public String getUserId() { return userId; }
    public String getPaperId() { return paperId; }
    public String getTimestamp() { return timestamp; }
    @Override public String toString() {
        return timestamp + " | " + action + " | user=" + userId + " | paper=" + paperId + " | " + details;
    }
}
