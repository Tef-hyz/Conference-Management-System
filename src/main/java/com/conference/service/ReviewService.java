package com.conference.service;

import com.conference.model.*;
import com.conference.persistence.ConferenceRepository;
import java.util.*;

public class ReviewService {
    private final ConferenceRepository repo;
    private final ConflictChecker checker = ConflictChecker.standard();
    public ReviewService(ConferenceRepository repo) { this.repo = repo; }
    public AnonymousPaperView getAssignedPaperView(ReviewerRole reviewer, String paperId) {
        if (reviewer == null) return null;
        WorkflowRules.requireRole(reviewer, repo);
        Paper paper = repo.findPaper(paperId);
        if (paper == null || paper.getStatus() == PaperStatus.WITHDRAWN
                || !paper.hasAssignedReviewer(reviewer.getAccount().getUserId())
                || !checker.isEligible(reviewer, paper)) return null;
        return paper.toAnonymousView();
    }
    public Review submitReview(ReviewerRole reviewer, String paperId, int score, int confidence, String comments) {
        WorkflowRules.requireRole(reviewer, repo);
        WorkflowRules.requirePhase(repo, ConferencePhase.REVIEW);
        Paper paper = repo.findPaper(paperId);
        if (paper == null) throw new IllegalArgumentException("Paper " + paperId + " does not exist.");
        if (paper.getStatus() != PaperStatus.UNDER_REVIEW) throw new IllegalStateException("Paper is not under review.");
        if (getAssignedPaperView(reviewer, paper.getPaperId()) == null) throw new IllegalStateException("You are not assigned or have a conflict of interest.");
        String reviewerId = reviewer.getAccount().getUserId();
        for (Review existing : repo.findReviewsByPaper(paper.getPaperId()))
            if (existing.getReviewerId().equals(reviewerId)) throw new IllegalStateException("You have already reviewed paper " + paper.getPaperId() + ".");
        final Review[] result = new Review[1];
        repo.transaction(() -> {
            Review review = new Review(repo.nextReviewId(), paper.getPaperId(), reviewerId, score, confidence, comments);
            repo.saveReview(review);
            repo.recordAudit(new AuditEvent("REVIEW_SUBMITTED", reviewerId, paper.getPaperId(), "Review=" + review.getReviewId()));
            result[0] = review;
        });
        return result[0];
    }
    public void updateExpertise(ReviewerRole reviewer, List<String> topics) {
        WorkflowRules.requireRole(reviewer, repo);
        repo.transaction(() -> {
            reviewer.setExpertiseTopics(topics);
            repo.saveUser(reviewer.getAccount());
            repo.recordAudit(new AuditEvent("EXPERTISE_UPDATED", reviewer.getAccount().getUserId(), "-", topics.toString()));
        });
    }
    public void declareConflict(ReviewerRole reviewer, String id) {
        WorkflowRules.requireRole(reviewer, repo);
        if (id == null || id.trim().isEmpty()) throw new IllegalArgumentException("Conflict ID is required.");
        String value = id.trim().toUpperCase(Locale.ROOT);
        if (repo.findPaper(value) == null && repo.findUser(value) == null) throw new IllegalArgumentException("Unknown paper or author ID.");
        // No retrospective rewrite of published decisions. Before Decision, drop the affected
        // assignment, exclude its review, keep history, and let Chair assign a replacement.
        if (repo.getConfiguration().getPhase() == ConferencePhase.DECISION) throw new IllegalStateException("Conflicts cannot change after decisions are released.");
        repo.transaction(() -> {
            reviewer.declareConflict(value);
            for (String paperId : new ArrayList<String>(reviewer.getAssignedPaperIds())) {
                Paper paper = repo.findPaper(paperId);
                if (paper != null && !checker.isEligible(reviewer, paper)) {
                    paper.removeAssignedReviewer(reviewer.getAccount().getUserId());
                    repo.savePaper(paper);
                    repo.recordAudit(new AuditEvent("CONFLICT_UNASSIGNED", reviewer.getAccount().getUserId(), paperId, "Conflict=" + value + "; any prior review excluded from completion."));
                }
            }
            repo.saveUser(reviewer.getAccount());
            repo.recordAudit(new AuditEvent("CONFLICT_DECLARED", reviewer.getAccount().getUserId(), value, "Conflict=" + value));
        });
    }
    public List<Review> getReviewsForPaper(String paperId) {
        Paper paper = repo.findPaper(paperId);
        return paper == null ? new ArrayList<Review>() : new DecisionService(repo).validReviews(paper);
    }
    public List<Review> getReviewsByReviewer(String reviewerId) {
        List<Review> visible = new ArrayList<Review>();
        for (Review review : repo.findReviewsByReviewer(reviewerId)) {
            Paper paper = repo.findPaper(review.getPaperId());
            if (paper != null && paper.getStatus() != PaperStatus.WITHDRAWN && paper.getAssignedReviewerIds().contains(reviewerId)) visible.add(review);
        }
        return visible;
    }
}
