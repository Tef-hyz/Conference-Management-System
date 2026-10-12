package com.conference.model;

import com.conference.persistence.ConferenceRepository;
import com.conference.service.AssignmentService;

/**
 * Programme chair role: assigns reviewers (delegating the eligibility logic
 * to AssignmentService) and makes the final accept/reject decision.
 */
public class ChairRole extends ConferenceRole {

    private static final long serialVersionUID = 1L;

    public ChairRole(UserAccount account) {
        super(account);
    }

    /**
     * Assigns one reviewer to a paper after conflict-of-interest and
     * workload checks performed by the AssignmentService.
     *
     * @return true if the assignment was made.
     */
    public boolean assignReviewer(Paper paper, ReviewerRole reviewer, AssignmentService service) {
        return service.assignReviewer(paper, reviewer);
    }

    /**
     * Makes the final decision on a paper. Only papers UNDER_REVIEW can be decided.
     *
     * @return true if the decision was recorded.
     */
    public boolean makeDecision(Paper paper, boolean accept, ConferenceRepository repo) {
        if (paper == null || paper.getStatus() != PaperStatus.UNDER_REVIEW) {
            return false;
        }
        paper.setStatus(accept ? PaperStatus.ACCEPTED : PaperStatus.REJECTED);
        repo.savePaper(paper);
        return true;
    }

    @Override
    public String getRoleName() {
        return "Chair";
    }
}
