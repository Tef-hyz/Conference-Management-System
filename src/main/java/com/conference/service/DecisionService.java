package com.conference.service;

import com.conference.model.*;
import com.conference.persistence.ConferenceRepository;
import java.util.*;

/** Average, spread and recommendation use the same valid reviews as the completion gate. */
public class DecisionService {
    private final ConferenceRepository repo;
    public DecisionService(ConferenceRepository repo) { this.repo = repo; }
    public List<Review> validReviews(Paper paper) {
        List<Review> result = new ArrayList<Review>();
        Set<String> ids = new HashSet<String>();
        ConflictChecker checker = ConflictChecker.standard();
        for (Review review : repo.findReviewsByPaper(paper.getPaperId())) {
            UserAccount user = repo.findUser(review.getReviewerId());
            ReviewerRole r = user == null ? null : user.getRole(ReviewerRole.class);
            if (r != null && paper.getAssignedReviewerIds().contains(user.getUserId())
                    && r.isAssignedTo(paper.getPaperId()) && checker.isEligible(r, paper) && ids.add(user.getUserId())) result.add(review);
        }
        return result;
    }
    public boolean isReady(Paper paper) {
        return paper != null && paper.getStatus() == PaperStatus.UNDER_REVIEW
                && validReviews(paper).size() >= repo.getConfiguration().getRequiredReviewers();
    }
    public double average(Paper paper) {
        List<Review> reviews = validReviews(paper);
        if (reviews.isEmpty()) return 0;
        double sum = 0; for (Review review : reviews) sum += review.getScore();
        return sum / reviews.size();
    }
    public boolean hasDisagreement(Paper paper) {
        List<Review> reviews = validReviews(paper);
        if (reviews.size() < 2) return false;
        int min = 5, max = 1;
        for (Review review : reviews) { min = Math.min(min, review.getScore()); max = Math.max(max, review.getScore()); }
        return max - min >= repo.getConfiguration().getDisagreementThreshold();
    }
    public boolean recommendsAccept(Paper paper) {
        return average(paper) >= repo.getConfiguration().getAcceptanceThreshold();
    }
    public boolean makeDecision(ChairRole chair, Paper paper, boolean accept, String reason) {
        WorkflowRules.requireRole(chair, repo);
        WorkflowRules.requirePhase(repo, ConferencePhase.DECISION);
        if (paper == null || repo.findPaper(paper.getPaperId()) != paper || !isReady(paper)) return false;
        boolean override = accept != recommendsAccept(paper);
        if (override && (reason == null || reason.trim().isEmpty())) throw new IllegalArgumentException("A reason is required when overriding the recommendation.");
        repo.transaction(() -> {
            paper.recordDecision(accept, chair.getAccount().getUserId(), override ? reason : "");
            repo.savePaper(paper);
            repo.recordAudit(new AuditEvent(override ? "DECISION_OVERRIDE" : "FINAL_DECISION", chair.getAccount().getUserId(), paper.getPaperId(),
                    "Decision=" + paper.getStatus() + "; average=" + average(paper) + "; disagreement=" + hasDisagreement(paper) + "; reason=" + paper.getOverrideReason()));
        });
        return true;
    }
    /** Returns strings without review IDs or reviewer IDs, only for an authorised paper author. */
    public List<String> authorFeedback(AuthorRole author, String paperId) {
        WorkflowRules.requireRole(author, repo);
        Paper paper = repo.findPaper(paperId);
        if (paper == null || !paper.getAuthorIds().contains(author.getAccount().getUserId()))
            throw new IllegalStateException("This is not your paper.");
        if (paper.getStatus() != PaperStatus.ACCEPTED && paper.getStatus() != PaperStatus.REJECTED)
            throw new IllegalStateException("The decision has not been released.");
        List<String> feedback = new ArrayList<String>();
        feedback.add("Decision: " + paper.getStatus());
        for (Review review : validReviews(paper))
            feedback.add("Score=" + review.getScore() + " | confidence=" + review.getConfidence() + " | " + review.getComments());
        return feedback;
    }
}
