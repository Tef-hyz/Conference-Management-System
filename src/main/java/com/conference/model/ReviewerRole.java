package com.conference.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.conference.persistence.ConferenceRepository;

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
    // Query context only: assignments live on Paper and completion lives in Review.
    // Rebound by the repository after load/rollback; never serialized or exposed to the CUI.
    private transient ConferenceRepository repository;

    public void attachRepository(ConferenceRepository repository) {
        if (repository == null) throw new IllegalArgumentException("Repository is required.");
        this.repository = repository;
    }

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
        return repository == null ? 0 : repository.countPendingReviews(account.getUserId());
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

    // ---- derived assignments: no second association list or Paper entities ----
    public boolean isAssignedTo(String paperId) {
        return getAssignedPaperIds().contains(paperId);
    }

    public List<String> getAssignedPaperIds() {
        return repository == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(repository.findPaperIdsByReviewer(account.getUserId()));
    }

    @Override
    public String getRoleName() {
        return "Reviewer";
    }
}
