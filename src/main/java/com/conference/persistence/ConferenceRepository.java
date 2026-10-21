package com.conference.persistence;

import com.conference.model.Paper;
import com.conference.model.Review;
import com.conference.model.ReviewerRole;
import com.conference.model.UserAccount;
import com.conference.model.ConferenceConfiguration;
import com.conference.model.AuditEvent;

import java.util.List;

/**
 * Repository abstraction (Dependency Inversion Principle):
 * roles and services depend on this interface, not on any concrete storage.
 * The provided implementation is file-based (FileDataStore), but it could be
 * swapped for a database implementation without touching business logic.
 */
public interface ConferenceRepository {

    // ---- ID generation ----
    String nextUserId();

    String nextPaperId();

    String nextReviewId();

    // ---- Users ----
    void saveUser(UserAccount user);

    UserAccount findUser(String userId);

    List<UserAccount> findAllUsers();

    List<ReviewerRole> findAllReviewers();

    // ---- Papers ----
    void savePaper(Paper paper);

    Paper findPaper(String paperId);

    List<Paper> findAllPapers();

    List<Paper> findPapersByAuthor(String authorId);

    // ---- Reviews ----
    void saveReview(Review review);

    List<Review> findReviewsByPaper(String paperId);

    List<Review> findReviewsByReviewer(String reviewerId);

    /** Flushes the current state to persistent storage. */
    void persist();

    /** Commits related changes together or restores the in-memory snapshot on failure. */
    void transaction(Runnable action);

    ConferenceConfiguration getConfiguration();

    void saveConfiguration(ConferenceConfiguration configuration);

    void recordAudit(AuditEvent event);

    List<AuditEvent> findAuditEvents();
}
