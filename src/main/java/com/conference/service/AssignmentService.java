package com.conference.service;

import com.conference.model.*;
import com.conference.persistence.ConferenceRepository;
import java.util.*;

/** Both routes enforce the same phase, conflict, capacity and two-reviewer cap. */
public class AssignmentService {
    public static final int REQUIRED_REVIEWS_PER_PAPER = 2;
    private final ConflictChecker conflictChecker;
    private final ConferenceRepository repo;
    public AssignmentService(ConflictChecker checker, ConferenceRepository repo) {
        this.conflictChecker = checker;
        this.repo = repo;
    }
    public int topicMatch(Paper paper, ReviewerRole reviewer) {
        Set<String> topics = new HashSet<String>();
        for (String value : paper.getTopics()) topics.add(value.trim().toLowerCase(Locale.ROOT));
        Set<String> matches = new HashSet<String>();
        for (String value : reviewer.getExpertiseTopics()) {
            String topic = value.trim().toLowerCase(Locale.ROOT);
            if (topics.contains(topic)) matches.add(topic);
        }
        return matches.size();
    }
    public boolean isEligible(Paper paper, ReviewerRole reviewer) {
        if (paper == null || reviewer == null || repo.findPaper(paper.getPaperId()) != paper
                || repo.findUser(reviewer.getAccount().getUserId()) != reviewer.getAccount()
                || reviewer.getAccount().getRole(ReviewerRole.class) != reviewer) return false;
        if (repo.getConfiguration().getPhase() != ConferencePhase.REVIEW) return false;
        if (paper.getStatus() != PaperStatus.SUBMITTED && paper.getStatus() != PaperStatus.UNDER_REVIEW) return false;
        if (paper.getAssignedReviewerIds().size() >= repo.getConfiguration().getRequiredReviewers()
                || paper.getAssignedReviewerIds().size() >= 2) return false;
        String id = reviewer.getAccount().getUserId();
        return !paper.getAssignedReviewerIds().contains(id) && !paper.getAuthorIds().contains(id)
                && reviewer.currentWorkload() < Math.min(reviewer.getMaxWorkload(), repo.getConfiguration().getMaxWorkload())
                && conflictChecker.isEligible(reviewer, paper);
    }
    public int autoAssign(Paper paper, List<ReviewerRole> reviewers, ChairRole chair) {
        WorkflowRules.requireRole(chair, repo);
        WorkflowRules.requirePhase(repo, ConferencePhase.REVIEW);
        if (paper == null || reviewers == null) return 0;
        List<ReviewerRole> ranked = new ArrayList<ReviewerRole>();
        for (ReviewerRole reviewer : reviewers) if (isEligible(paper, reviewer) && topicMatch(paper, reviewer) > 0) ranked.add(reviewer);
        ranked.sort(Comparator.comparingInt((ReviewerRole r) -> topicMatch(paper, r)).reversed()
                .thenComparingInt(ReviewerRole::currentWorkload).thenComparing(r -> r.getAccount().getUserId()));
        final int[] count = {0};
        repo.transaction(() -> {
            for (ReviewerRole reviewer : ranked) if (assignReviewer(paper, reviewer, chair)) count[0]++;
        });
        return count[0];
    }
    public boolean assignReviewer(Paper paper, ReviewerRole reviewer, ChairRole chair) {
        WorkflowRules.requireRole(chair, repo);
        WorkflowRules.requirePhase(repo, ConferencePhase.REVIEW);
        if (!isEligible(paper, reviewer)) return false;
        repo.transaction(() -> {
            String id = reviewer.getAccount().getUserId();
            paper.addAssignedReviewer(id);
            reviewer.addAssignedPaper(paper.getPaperId());
            if (paper.getStatus() == PaperStatus.SUBMITTED) paper.setStatus(PaperStatus.UNDER_REVIEW);
            repo.savePaper(paper);
            repo.saveUser(reviewer.getAccount());
            repo.recordAudit(new AuditEvent("REVIEWER_ASSIGNED", chair.getAccount().getUserId(), paper.getPaperId(), "Reviewer=" + id));
        });
        return true;
    }
}
