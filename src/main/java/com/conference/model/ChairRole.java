package com.conference.model;

import com.conference.persistence.ConferenceRepository;
import com.conference.service.AssignmentService;
import com.conference.service.DecisionService;

public class ChairRole extends ConferenceRole {
    private static final long serialVersionUID = 1L;
    public ChairRole(UserAccount account) { super(account); }
    public boolean assignReviewer(Paper paper, ReviewerRole reviewer, AssignmentService service) {
        return service.assignReviewer(paper, reviewer, this);
    }
    public boolean makeDecision(Paper paper, boolean accept, ConferenceRepository repo) {
        return makeDecision(paper, accept, "", repo);
    }
    public boolean makeDecision(Paper paper, boolean accept, String reason, ConferenceRepository repo) {
        return new DecisionService(repo).makeDecision(this, paper, accept, reason);
    }
    @Override public String getRoleName() { return "Chair"; }
}
