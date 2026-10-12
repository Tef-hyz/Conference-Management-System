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

    public Paper(String paperId, String title, String abstractText,
                 List<String> topics, List<String> authorIds, List<String> institutions) {
        if (paperId == null || paperId.trim().isEmpty()) {
            throw new IllegalArgumentException("paperId must not be empty");
        }
        this.paperId = paperId;
        this.title = title;
        this.abstractText = abstractText;
        this.topics = new ArrayList<String>(topics);
        this.authorIds = new ArrayList<String>(authorIds);
        this.institutions = new ArrayList<String>(institutions);
    }

    /**
     * Projects this paper into an anonymous view (double-blind review):
     * author IDs and institutions are deliberately excluded.
     */
    public AnonymousPaperView toAnonymousView() {
        return new AnonymousPaperView(paperId, title, abstractText, topics);
    }

    public void addAssignedReviewer(String reviewerId) {
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
        this.title = title;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public void setAbstractText(String abstractText) {
        this.abstractText = abstractText;
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
        this.status = status;
    }

    @Override
    public String toString() {
        return String.format("[%s] \"%s\" | status=%s | topics=%s | reviewers=%s",
                paperId, title, status, topics, assignedReviewerIds);
    }
}
