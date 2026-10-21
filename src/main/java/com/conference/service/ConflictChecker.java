package com.conference.service;

import com.conference.model.Paper;
import com.conference.model.ReviewerRole;

import java.util.ArrayList;
import java.util.List;

/**
 * Aggregates all registered conflict-of-interest rules and decides
 * whether a reviewer is eligible for a paper.
 */
public class ConflictChecker {

    public static ConflictChecker standard() {
        ConflictChecker checker = new ConflictChecker();
        checker.addRule(new AuthorConflictRule());
        checker.addRule(new SameInstitutionRule());
        checker.addRule(new DeclaredConflictRule());
        return checker;
    }

    private final List<ConflictRule> rules = new ArrayList<ConflictRule>();

    public void addRule(ConflictRule rule) {
        if (rule != null) {
            rules.add(rule);
        }
    }

    /** A reviewer is eligible only when NO rule reports a conflict. */
    public boolean isEligible(ReviewerRole reviewer, Paper paper) {
        for (ConflictRule rule : rules) {
            if (rule.hasConflict(reviewer, paper)) {
                return false;
            }
        }
        return true;
    }

    /** Names of the rules violated by this reviewer/paper pair (for user feedback). */
    public List<String> findViolatedRules(ReviewerRole reviewer, Paper paper) {
        List<String> violated = new ArrayList<String>();
        for (ConflictRule rule : rules) {
            if (rule.hasConflict(reviewer, paper)) {
                violated.add(rule.getRuleName());
            }
        }
        return violated;
    }
}
