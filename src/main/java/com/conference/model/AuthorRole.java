package com.conference.model;

import com.conference.persistence.ConferenceRepository;
import com.conference.service.WorkflowRules;
import java.util.*;

/** Submission ownership is the first author ID; co-authors can read status and feedback. */
public class AuthorRole extends ConferenceRole {
    private static final long serialVersionUID = 1L;
    public AuthorRole(UserAccount account) { super(account); }

    public Paper submitPaper(String title, String abstractText, List<String> topics,
            List<String> institutions, ConferenceRepository repo) {
        return submitPaper(title, abstractText, topics, institutions, Collections.<String>emptyList(), repo);
    }
    public Paper submitPaper(String title, String abstractText, List<String> topics,
            List<String> institutions, List<String> coAuthorIds, ConferenceRepository repo) {
        WorkflowRules.requireRole(this, repo);
        WorkflowRules.requirePhase(repo, ConferencePhase.SUBMISSION);
        if (institutions == null || institutions.isEmpty()) throw new IllegalArgumentException("Author institutions must not be empty.");
        LinkedHashSet<String> authors = new LinkedHashSet<String>();
        LinkedHashSet<String> affiliations = new LinkedHashSet<String>();
        authors.add(account.getUserId());
        affiliations.add(account.getInstitution());
        if (coAuthorIds != null) for (String id : coAuthorIds) {
            UserAccount co = repo.findUser(id);
            if (co == null || co.getRole(AuthorRole.class) == null)
                throw new IllegalArgumentException("Co-author must be a registered Author: " + id);
            authors.add(co.getUserId());
            affiliations.add(co.getInstitution());
        }
        for (String value : institutions) {
            if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("Institution must not be empty.");
            affiliations.add(value.trim());
        }
        final Paper[] result = new Paper[1];
        repo.transaction(() -> {
            Paper paper = new Paper(repo.nextPaperId(), title, abstractText, topics,
                    new ArrayList<String>(authors), new ArrayList<String>(affiliations));
            repo.savePaper(paper);
            repo.recordAudit(new AuditEvent("PAPER_SUBMITTED", account.getUserId(), paper.getPaperId(), "Authors=" + authors));
            result[0] = paper;
        });
        return result[0];
    }
    public boolean withdrawPaper(String paperId, ConferenceRepository repo) {
        WorkflowRules.requireRole(this, repo);
        if (repo.getConfiguration().getPhase() == ConferencePhase.DECISION) return false;
        final Paper paper = repo.findPaper(paperId);
        if (paper == null || !paper.getSubmittingAuthorId().equals(account.getUserId())
                || (paper.getStatus() != PaperStatus.SUBMITTED && paper.getStatus() != PaperStatus.UNDER_REVIEW)) return false;
        repo.transaction(() -> {
            for (String id : new ArrayList<String>(paper.getAssignedReviewerIds())) {
                paper.removeAssignedReviewer(id);
            }
            paper.setStatus(PaperStatus.WITHDRAWN);
            repo.savePaper(paper);
            repo.recordAudit(new AuditEvent("PAPER_WITHDRAWN", account.getUserId(), paper.getPaperId(), "Assignments removed; reviewer capacity released."));
        });
        return true;
    }
    @Override public String getRoleName() { return "Author"; }
}
