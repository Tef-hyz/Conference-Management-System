package com.conference.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A submitted paper. Author identity is encapsulated here;
 * reviewers only ever see the AnonymousPaperView projection.
 */
public class Paper implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String paperId;
    private String title;
    private String abstractText;
    private final List<String> topics;
    private final List<String> authorIds;
    private final List<String> institutions;
    private final List<String> assignedReviewerIds = new ArrayList<String>();
    private PaperStatus status = PaperStatus.SUBMITTED;
    private String overrideReason;
    private String decisionBy;
    private String decisionTime;

    public Paper(String paperId, String title, String abstractText,
                 List<String> topics, List<String> authorIds, List<String> institutions) {
        if (paperId == null || paperId.trim().isEmpty()) {
            throw new IllegalArgumentException("paperId must not be empty");
        }
        this.paperId = paperId;
        setTitle(title);
        setAbstractText(abstractText);
        this.topics = requiredList(topics, "Topics");
        this.authorIds = requiredList(authorIds, "Authors");
        this.institutions = requiredList(institutions, "Institutions");
    }

    /**
     * Projects this paper into an anonymous view (double-blind review):
     * author IDs and institutions are deliberately excluded.
     */
    public AnonymousPaperView toAnonymousView() {
        return new AnonymousPaperView(paperId, title, abstractText, topics);
    }

    public void addAssignedReviewer(String reviewerId) {
        if (reviewerId == null || reviewerId.trim().isEmpty()) throw new IllegalArgumentException("Reviewer ID is required.");
        if (!assignedReviewerIds.contains(reviewerId) && assignedReviewerIds.size() >= 2)
            throw new IllegalStateException("A paper cannot have more than two reviewers.");
        if (!assignedReviewerIds.contains(reviewerId)) {
            assignedReviewerIds.add(reviewerId);
        }
    }

    public String getPaperId() {
        return paperId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = required(title, "Paper title");
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = required(abstractText, "Abstract");
    }

    public List<String> getTopics() {
        return Collections.unmodifiableList(topics);
    }

    public List<String> getAuthorIds() {
        return Collections.unmodifiableList(authorIds);
    }

    public List<String> getInstitutions() {
        return Collections.unmodifiableList(institutions);
    }

    public List<String> getAssignedReviewerIds() {
        return Collections.unmodifiableList(assignedReviewerIds);
    }

    public PaperStatus getStatus() {
        return status;
    }

    public void setStatus(PaperStatus status) {
        if (status == null) throw new IllegalArgumentException("Paper status is required.");
        if (this.status == status) return;
        boolean valid = (this.status == PaperStatus.SUBMITTED && (status == PaperStatus.UNDER_REVIEW || status == PaperStatus.WITHDRAWN))
                || (this.status == PaperStatus.UNDER_REVIEW && (status == PaperStatus.WITHDRAWN || status == PaperStatus.ACCEPTED || status == PaperStatus.REJECTED));
        if (!valid) throw new IllegalStateException("Invalid paper status transition.");
        this.status = status;
    }

    public void removeAssignedReviewer(String reviewerId) { assignedReviewerIds.remove(reviewerId); }
    public String getSubmittingAuthorId() { return authorIds.get(0); }
    public String getOverrideReason() { return overrideReason == null ? "" : overrideReason; }
    public String getDecisionBy() { return decisionBy; }
    public String getDecisionTime() { return decisionTime; }
    public void recordDecision(boolean accept, String userId, String reason) {
        if (status != PaperStatus.UNDER_REVIEW) throw new IllegalStateException("Paper is not under review.");
        decisionBy = required(userId, "Decision user");
        overrideReason = reason == null ? "" : reason.trim();
        decisionTime = java.time.Instant.now().toString();
        setStatus(accept ? PaperStatus.ACCEPTED : PaperStatus.REJECTED);
    }
    private static String required(String value, String field) {
        if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException(field + " must not be empty.");
        return value.trim();
    }
    private static List<String> requiredList(List<String> values, String field) {
        if (values == null || values.isEmpty()) throw new IllegalArgumentException(field + " must not be empty.");
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            String item = required(value, field);
            if (!result.contains(item)) result.add(item);
        }
        return result;
    }

    @Override
    public String toString() {
        return String.format("[%s] \"%s\" | status=%s | topics=%s | reviewers=%s",
                paperId, title, status, topics, assignedReviewerIds);
    }
}
