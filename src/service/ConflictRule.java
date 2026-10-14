package com.conference.service;

import com.conference.model.Paper;
import com.conference.model.ReviewerRole;

/**
 * Strategy interface for conflict-of-interest rules (Open/Closed Principle):
 * new rules can be added without modifying ConflictChecker.
 */
public interface ConflictRule {

    /**
     * @return true if assigning this reviewer to this paper would be
     *         a conflict of interest.
     */
    boolean hasConflict(ReviewerRole reviewer, Paper paper);

    /** Human-readable name of the rule, used in feedback messages. */
    String getRuleName();
}
