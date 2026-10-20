package com.conference.model;

import com.conference.persistence.ConferenceRepository;

import java.util.Collections;
import java.util.List;

/**
 * Author role: submits and withdraws papers.
 */
public class AuthorRole extends ConferenceRole {

    private static final long serialVersionUID = 1L;

    public AuthorRole(UserAccount account) {
        super(account);
    }

    /**
     * Creates a new paper authored by this account and stores it.
     * The paper starts in SUBMITTED status.
     */
    public Paper submitPaper(String title, String abstractText, List<String> topics,
                             List<String> institutions, ConferenceRepository repo) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Paper title must not be empty");
        }
        String paperId = repo.nextPaperId();
        Paper paper = new Paper(paperId, title, abstractText,
                topics == null ? Collections.<String>emptyList() : topics,
                Collections.singletonList(account.getUserId()),
                institutions == null ? Collections.<String>emptyList() : institutions);
        repo.savePaper(paper);
        return paper;
    }

    /**
     * Withdraws a paper previously submitted by this author.
     *
     * @return true if the paper was withdrawn, false if it does not exist,
     *         belongs to someone else, or is already decided (accepted/rejected).
     */
    public boolean withdrawPaper(String paperId, ConferenceRepository repo) {
        Paper paper = repo.findPaper(paperId);
        if (paper == null || !paper.getAuthorIds().contains(account.getUserId())) {
            return false;
        }
        if (paper.getStatus() == PaperStatus.ACCEPTED || paper.getStatus() == PaperStatus.REJECTED) {
            return false;
        }
        paper.setStatus(PaperStatus.WITHDRAWN);
        repo.savePaper(paper);
        return true;
    }

    @Override
    public String getRoleName() {
        return "Author";
    }
}
