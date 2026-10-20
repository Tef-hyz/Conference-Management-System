package com.conference.service;

import com.conference.model.Paper;
import com.conference.model.ReviewerRole;

/**
 * Conflict rule: a reviewer must not review a paper coming from
 * their own institution.
 */
public class SameInstitutionRule implements ConflictRule {

    @Override
    public boolean hasConflict(ReviewerRole reviewer, Paper paper) {
        String reviewerInstitution = reviewer.getAccount().getInstitution();
        if (reviewerInstitution == null || reviewerInstitution.trim().isEmpty()) {
            return false;
        }
        for (String paperInstitution : paper.getInstitutions()) {
            if (reviewerInstitution.equalsIgnoreCase(paperInstitution.trim())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getRuleName() {
        return "SameInstitutionRule";
    }
}
