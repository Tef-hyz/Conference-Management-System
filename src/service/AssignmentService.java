package com.conference.service;

import com.conference.model.Paper;
import com.conference.model.PaperStatus;
import com.conference.model.ReviewerRole;
import com.conference.persistence.ConferenceRepository;

import java.util.List;

/**
 * Assigns reviewers to papers, enforcing conflict-of-interest rules
 * (via ConflictChecker) and reviewer workload limits.
 */
public class AssignmentService {

    /** Number of reviewers each paper should ideally receive. */
    public static final int REQUIRED_REVIEWS_PER_PAPER = 2;

    private final ConflictChecker conflictChecker;
    private final ConferenceRepository repo;

    public AssignmentService(ConflictChecker conflictChecker, ConferenceRepository repo) {
        this.conflictChecker = conflictChecker;
        this.repo = repo;
    }

    /**
     * Tries to bring the paper up to the required number of reviewers,
     * picking eligible reviewers with spare capacity in list order.
     *
     * @return how many new reviewers were assigned.
     */
    public int autoAssign(Paper paper, List<ReviewerRole> reviewers) {
        int newlyAssigned = 0;
        if (paper == null || reviewers == null) {
            return 0;
        }
        for (ReviewerRole reviewer : reviewers) {
            if (paper.getAssignedReviewerIds().size() >= REQUIRED_REVIEWS_PER_PAPER) {
                break;
            }
            if (assignReviewer(paper, reviewer)) {
                newlyAssigned++;
            }
        }
        return newlyAssigned;
    }

    /**
     * Assigns a single reviewer after validation:
     * paper must be assignable, reviewer must have capacity and no conflict.
     *
     * @return true if the assignment was made.
     */
    public boolean assignReviewer(Paper paper, ReviewerRole reviewer) {
        if (paper == null || reviewer == null) {
            return false;
        }
        if (paper.getStatus() != PaperStatus.SUBMITTED && paper.getStatus() != PaperStatus.UNDER_REVIEW) {
            return false; // withdrawn or decided papers cannot be assigned
        }
        String reviewerId = reviewer.getAccount().getUserId();
        if (paper.getAssignedReviewerIds().contains(reviewerId)) {
            return false; // already assigned
        }
        if (!reviewer.hasCapacity()) {
            return false; // workload limit reached
        }
        if (!conflictChecker.isEligible(reviewer, paper)) {
            return false; // conflict of interest
        }
        paper.addAssignedReviewer(reviewerId);
        reviewer.addAssignedPaper(paper.getPaperId());
        if (paper.getStatus() == PaperStatus.SUBMITTED) {
            paper.setStatus(PaperStatus.UNDER_REVIEW);
        }
        repo.savePaper(paper);
        repo.saveUser(reviewer.getAccount());
        return true;
    }
}
