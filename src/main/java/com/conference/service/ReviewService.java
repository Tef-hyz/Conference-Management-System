package com.conference.service;

import com.conference.model.AnonymousPaperView;
import com.conference.model.Paper;
import com.conference.model.PaperStatus;
import com.conference.model.Review;
import com.conference.model.ReviewerRole;
import com.conference.persistence.ConferenceRepository;

import java.util.List;

/**
 * Review workflow: reviewers read anonymous paper views and submit reviews.
 */
public class ReviewService {

    private final ConferenceRepository repo;

    public ReviewService(ConferenceRepository repo) {
        this.repo = repo;
    }

    /**
     * Returns the anonymous view of a paper assigned to this reviewer.
     *
     * @return the view, or null if the paper does not exist or is not
     *         assigned to this reviewer.
     */
    public AnonymousPaperView getAssignedPaperView(ReviewerRole reviewer, String paperId) {
        Paper paper = repo.findPaper(paperId);
        if (paper == null || reviewer == null || !reviewer.isAssignedTo(paperId)) {
            return null;
        }
        return paper.toAnonymousView();
    }

    /**
     * Submits a review. Validation rules:
     * the paper must be UNDER_REVIEW, assigned to this reviewer,
     * and the reviewer must not have reviewed it before.
     *
     * @throws IllegalArgumentException / IllegalStateException with a user-friendly
     *         message when validation fails.
     */
    public Review submitReview(ReviewerRole reviewer, String paperId,
                               int score, int confidence, String comments) {
        Paper paper = repo.findPaper(paperId);
        if (paper == null) {
            throw new IllegalArgumentException("Paper " + paperId + " does not exist.");
        }
        if (paper.getStatus() != PaperStatus.UNDER_REVIEW) {
            throw new IllegalStateException("Paper " + paperId + " is not under review (status: "
                    + paper.getStatus() + ").");
        }
        if (reviewer == null || !reviewer.isAssignedTo(paperId)) {
            throw new IllegalStateException("You are not assigned to paper " + paperId + ".");
        }
        String reviewerId = reviewer.getAccount().getUserId();
        for (Review existing : repo.findReviewsByPaper(paperId)) {
            if (existing.getReviewerId().equals(reviewerId)) {
                throw new IllegalStateException("You have already reviewed paper " + paperId + ".");
            }
        }
        Review review = new Review(repo.nextReviewId(), paperId, reviewerId, score, confidence, comments);
        repo.saveReview(review);
        return review;
    }

    public List<Review> getReviewsForPaper(String paperId) {
        return repo.findReviewsByPaper(paperId);
    }

    public List<Review> getReviewsByReviewer(String reviewerId) {
        return repo.findReviewsByReviewer(reviewerId);
    }
}
