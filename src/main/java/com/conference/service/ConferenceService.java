package com.conference.service;

import com.conference.model.*;
import com.conference.persistence.ConferenceRepository;

/** Settings are frozen after Submission. Phases progress forward and block incomplete decisions. */
public class ConferenceService {
    private final ConferenceRepository repo;
    public ConferenceService(ConferenceRepository repo) { this.repo = repo; }
    public void configure(ChairRole chair, int count, int workload, double acceptance, int disagreement) {
        WorkflowRules.requireRole(chair, repo);
        WorkflowRules.requirePhase(repo, ConferencePhase.SUBMISSION);
        ConferenceConfiguration settings = new ConferenceConfiguration(count, workload, acceptance, disagreement, ConferencePhase.SUBMISSION);
        repo.transaction(() -> {
            repo.saveConfiguration(settings);
            for (ReviewerRole reviewer : repo.findAllReviewers()) {
                reviewer.setMaxWorkload(workload);
                repo.saveUser(reviewer.getAccount());
            }
            repo.recordAudit(new AuditEvent("CONFERENCE_CONFIGURED", chair.getAccount().getUserId(), "-", settings.toString()));
        });
    }
    public void changePhase(ChairRole chair, ConferencePhase next) {
        WorkflowRules.requireRole(chair, repo);
        ConferenceConfiguration current = repo.getConfiguration();
        if (next == null || next.ordinal() != current.getPhase().ordinal() + 1)
            throw new IllegalStateException("Phases must progress Submission -> Review -> Decision.");
        if (next == ConferencePhase.DECISION) {
            DecisionService decisions = new DecisionService(repo);
            for (Paper paper : repo.findAllPapers())
                if (paper.getStatus() != PaperStatus.WITHDRAWN && !decisions.isReady(paper))
                    throw new IllegalStateException("Required reviews are incomplete for " + paper.getPaperId() + ".");
        }
        repo.transaction(() -> {
            repo.saveConfiguration(current.withPhase(next));
            repo.recordAudit(new AuditEvent("PHASE_CHANGED", chair.getAccount().getUserId(), "-", current.getPhase() + " -> " + next));
        });
    }
    public void grantChair(ChairRole actor, String userId) {
        WorkflowRules.requireRole(actor, repo);
        UserAccount target = repo.findUser(userId);
        if (target == null) throw new IllegalArgumentException("User not found.");
        repo.transaction(() -> {
            target.addRole(new ChairRole(target));
            repo.saveUser(target);
            repo.recordAudit(new AuditEvent("CHAIR_GRANTED", actor.getAccount().getUserId(), "-", "Target=" + target.getUserId()));
        });
    }
}
