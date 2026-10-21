package com.conference.service;

import com.conference.model.Paper;
import com.conference.model.ReviewerRole;

/** Author identity is checked even when affiliation metadata is missing or inaccurate. */
public class AuthorConflictRule implements ConflictRule {
    @Override public boolean hasConflict(ReviewerRole reviewer, Paper paper) {
        return paper.getAuthorIds().contains(reviewer.getAccount().getUserId());
    }
    @Override public String getRuleName() { return "AuthorConflictRule"; }
}
