package com.conference.service;

import com.conference.model.Paper;
import com.conference.model.ReviewerRole;

/**
 * Conflict rule: honours the reviewer's self-declared conflicts,
 * which may reference a paper ID or an author's user ID.
 */
public class DeclaredConflictRule implements ConflictRule {

    @Override
    public boolean hasConflict(ReviewerRole reviewer, Paper paper) {
        for (String declared : reviewer.getDeclaredConflicts()) {
            if (declared.equalsIgnoreCase(paper.getPaperId())) {
                return true;
            }
            for (String authorId : paper.getAuthorIds()) {
                if (declared.equalsIgnoreCase(authorId)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getRuleName() {
        return "DeclaredConflictRule";
    }
}
