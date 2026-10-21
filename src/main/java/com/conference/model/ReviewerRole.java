package com.conference.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Collection;

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
    // Completion does not remove assignment: views, duplicate checks and decisions still need it.
    // Nullable only in older serialVersionUID=1 snapshots; rebuilt from saved reviews on load.
    private Set<String> completedPaperIds = new LinkedHashSet<String>();

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

    public void setExpertiseTopics(List<String> topics) {
        if (topics == null || topics.isEmpty()) throw new IllegalArgumentException("Expertise topics must not be empty.");
        List<String> validated = new ArrayList<String>();
        for (String topic : topics) {
            if (topic == null || topic.trim().isEmpty()) throw new IllegalArgumentException("Expertise topic must not be empty.");
            boolean duplicate = false;
            for (String value : validated) if (value.equalsIgnoreCase(topic.trim())) duplicate = true;
            if (!duplicate) validated.add(topic.trim());
        }
        expertiseTopics.clear();
        expertiseTopics.addAll(validated);
    }

    // ---- conflicts of interest ----
    public void declareConflict(String paperOrAuthorId) {
        if (paperOrAuthorId == null || paperOrAuthorId.trim().isEmpty())
            throw new IllegalArgumentException("Conflict ID must not be empty.");
        if (paperOrAuthorId != null && !paperOrAuthorId.trim().isEmpty()
                && !declaredConflicts.contains(paperOrAuthorId.trim())) {
            declaredConflicts.add(paperOrAuthorId.trim());
        }
    }

    public List<String> getDeclaredConflicts() {
        return Collections.unmodifiableList(declaredConflicts);
    }

    // ---- workload ----
    /** Number of assigned papers whose review has not yet been successfully submitted. */
    public int currentWorkload() {
        int pending = 0;
        for (String paperId : assignedPaperIds) {
            if (completedPaperIds == null || !completedPaperIds.contains(paperId)) pending++;
        }
        return pending;
    }

    public void markReviewCompleted(String paperId) {
        if (!isAssignedTo(paperId)) throw new IllegalStateException("Cannot complete an unassigned review.");
        if (completedPaperIds == null) completedPaperIds = new LinkedHashSet<String>();
        completedPaperIds.add(paperId);
    }

    /** The persisted Review records are authoritative, including for older snapshots. */
    public void restoreCompletedReviews(Collection<String> paperIds) {
        completedPaperIds = new LinkedHashSet<String>();
        for (String paperId : paperIds) if (isAssignedTo(paperId)) completedPaperIds.add(paperId);
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

    public void removeAssignedPaper(String paperId) {
        assignedPaperIds.remove(paperId);
        if (completedPaperIds != null) completedPaperIds.remove(paperId);
    }

    @Override
    public String getRoleName() {
        return "Reviewer";
    }
}
