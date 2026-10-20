package com.conference.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reviewer role: declares expertise topics and conflicts of interest,
 * and is assigned papers to review (within a workload limit).
 */
public class ReviewerRole extends ConferenceRole {

    private static final long serialVersionUID = 1L;
    public static final int DEFAULT_MAX_WORKLOAD = 3;

    private final List<String> expertiseTopics = new ArrayList<String>();
    /** Paper IDs or author user IDs this reviewer has declared a conflict with. */
    private final List<String> declaredConflicts = new ArrayList<String>();
    private int maxWorkload = DEFAULT_MAX_WORKLOAD;
    private final List<String> assignedPaperIds = new ArrayList<String>();

    public ReviewerRole(UserAccount account) {
        super(account);
    }

    // ---- expertise ----
    public void addExpertiseTopic(String topic) {
        if (topic != null && !topic.trim().isEmpty() && !expertiseTopics.contains(topic.trim())) {
            expertiseTopics.add(topic.trim());
        }
    }

    public List<String> getExpertiseTopics() {
        return Collections.unmodifiableList(expertiseTopics);
    }

    // ---- conflicts of interest ----
    public void declareConflict(String paperOrAuthorId) {
        if (paperOrAuthorId != null && !paperOrAuthorId.trim().isEmpty()
                && !declaredConflicts.contains(paperOrAuthorId.trim())) {
            declaredConflicts.add(paperOrAuthorId.trim());
        }
    }

    public List<String> getDeclaredConflicts() {
        return Collections.unmodifiableList(declaredConflicts);
    }

    // ---- workload ----
    public int currentWorkload() {
        return assignedPaperIds.size();
    }

    public boolean hasCapacity() {
        return currentWorkload() < maxWorkload;
    }

    public int getMaxWorkload() {
        return maxWorkload;
    }

    public void setMaxWorkload(int maxWorkload) {
        if (maxWorkload < 1) {
            throw new IllegalArgumentException("maxWorkload must be at least 1");
        }
        this.maxWorkload = maxWorkload;
    }

    // ---- assignments (used by AssignmentService) ----
    public boolean isAssignedTo(String paperId) {
        return assignedPaperIds.contains(paperId);
    }

    public void addAssignedPaper(String paperId) {
        if (!assignedPaperIds.contains(paperId)) {
            assignedPaperIds.add(paperId);
        }
    }

    public List<String> getAssignedPaperIds() {
        return Collections.unmodifiableList(assignedPaperIds);
    }

    @Override
    public String getRoleName() {
        return "Reviewer";
    }
}
