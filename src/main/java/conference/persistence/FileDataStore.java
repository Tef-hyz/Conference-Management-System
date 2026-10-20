package com.conference.persistence;

import com.conference.model.AuthorRole;
import com.conference.model.ChairRole;
import com.conference.model.Paper;
import com.conference.model.Review;
import com.conference.model.ReviewerRole;
import com.conference.model.UserAccount;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * File-based implementation of {@link ConferenceRepository}.
 * <p>
 * The full system state is serialized to {@code data/conference-data.ser}
 * (created automatically on first run, relative to the working directory).
 * Every mutating operation is written through to disk, so no manual
 * configuration or save step is required.
 */
public class FileDataStore implements ConferenceRepository {

    private static final String DATA_DIR = "data";
    private static final String DATA_FILE = "conference-data.ser";

    private final File storageFile;
    private DataStore store;

    /** Uses the default location {@code data/conference-data.ser}. */
    public FileDataStore() {
        this(new File(DATA_DIR, DATA_FILE));
    }

    public FileDataStore(File storageFile) {
        this.storageFile = storageFile;
        load();
    }

    // ------------------------------------------------------------------
    // File I/O
    // ------------------------------------------------------------------

    private void load() {
        if (storageFile.exists()) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(storageFile))) {
                store = (DataStore) in.readObject();
                System.out.println("[FileDataStore] Loaded existing data from " + storageFile.getPath());
                return;
            } catch (IOException | ClassNotFoundException | ClassCastException e) {
                System.err.println("[FileDataStore] Could not read data file (" + e.getMessage()
                        + "). Starting with fresh demo data.");
            }
        }
        store = new DataStore();
        seedDemoData();
        persist();
        System.out.println("[FileDataStore] First run: created demo data in " + storageFile.getPath());
        System.out.println("[FileDataStore] Demo users U001-U006, password for all: " + DEMO_PASSWORD);
    }

    @Override
    public synchronized void persist() {
        File parent = storageFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            System.err.println("[FileDataStore] Could not create directory " + parent.getPath());
            return;
        }
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(storageFile))) {
            out.writeObject(store);
        } catch (IOException e) {
            System.err.println("[FileDataStore] Failed to save data: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // ID generation
    // ------------------------------------------------------------------

    @Override
    public synchronized String nextUserId() {
        return String.format("U%03d", ++store.userSeq);
    }

    @Override
    public synchronized String nextPaperId() {
        return String.format("P%03d", ++store.paperSeq);
    }

    @Override
    public synchronized String nextReviewId() {
        return String.format("R%03d", ++store.reviewSeq);
    }

    // ------------------------------------------------------------------
    // Users
    // ------------------------------------------------------------------

    @Override
    public synchronized void saveUser(UserAccount user) {
        store.users.put(user.getUserId(), user);
        persist();
    }

    @Override
    public synchronized UserAccount findUser(String userId) {
        return store.users.get(userId);
    }

    @Override
    public synchronized List<UserAccount> findAllUsers() {
        return new ArrayList<UserAccount>(store.users.values());
    }

    @Override
    public synchronized List<ReviewerRole> findAllReviewers() {
        List<ReviewerRole> reviewers = new ArrayList<ReviewerRole>();
        for (UserAccount user : store.users.values()) {
            ReviewerRole role = user.getRole(ReviewerRole.class);
            if (role != null) {
                reviewers.add(role);
            }
        }
        return reviewers;
    }

    // ------------------------------------------------------------------
    // Papers
    // ------------------------------------------------------------------

    @Override
    public synchronized void savePaper(Paper paper) {
        store.papers.put(paper.getPaperId(), paper);
        persist();
    }

    @Override
    public synchronized Paper findPaper(String paperId) {
        return store.papers.get(paperId);
    }

    @Override
    public synchronized List<Paper> findAllPapers() {
        return new ArrayList<Paper>(store.papers.values());
    }

    @Override
    public synchronized List<Paper> findPapersByAuthor(String authorId) {
        List<Paper> result = new ArrayList<Paper>();
        for (Paper paper : store.papers.values()) {
            if (paper.getAuthorIds().contains(authorId)) {
                result.add(paper);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Reviews
    // ------------------------------------------------------------------

    @Override
    public synchronized void saveReview(Review review) {
        store.reviews.put(review.getReviewId(), review);
        persist();
    }

    @Override
    public synchronized List<Review> findReviewsByPaper(String paperId) {
        List<Review> result = new ArrayList<Review>();
        for (Review review : store.reviews.values()) {
            if (review.getPaperId().equals(paperId)) {
                result.add(review);
            }
        }
        return result;
    }

    @Override
    public synchronized List<Review> findReviewsByReviewer(String reviewerId) {
        List<Review> result = new ArrayList<Review>();
        for (Review review : store.reviews.values()) {
            if (review.getReviewerId().equals(reviewerId)) {
                result.add(review);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Demo seed (first run only) - keeps the CUI immediately usable
    // ------------------------------------------------------------------

    /** Default password for all seeded demo accounts (shown on first run). */
    public static final String DEMO_PASSWORD = "123456";

    private void seedDemoData() {
        UserAccount alice = new UserAccount(nextUserId(), "Alice Chen", "alice@cityu.edu.hk", "City University");
        alice.setPassword(DEMO_PASSWORD);
        alice.addRole(new AuthorRole(alice));
        saveUser(alice);

        UserAccount bob = new UserAccount(nextUserId(), "Bob Li", "bob@hku.hk", "HKU");
        bob.setPassword(DEMO_PASSWORD);
        bob.addRole(new AuthorRole(bob));
        saveUser(bob);

        UserAccount carol = new UserAccount(nextUserId(), "Carol Wong", "carol@polyu.hk", "PolyU");
        carol.setPassword(DEMO_PASSWORD);
        ReviewerRole carolReviewer = new ReviewerRole(carol);
        carolReviewer.addExpertiseTopic("Machine Learning");
        carolReviewer.addExpertiseTopic("NLP");
        carol.addRole(carolReviewer);
        saveUser(carol);

        UserAccount dave = new UserAccount(nextUserId(), "Dave Smith", "dave@ust.hk", "HKUST");
        dave.setPassword(DEMO_PASSWORD);
        ReviewerRole daveReviewer = new ReviewerRole(dave);
        daveReviewer.addExpertiseTopic("Machine Learning");
        daveReviewer.addExpertiseTopic("Computer Vision");
        dave.addRole(daveReviewer);
        saveUser(dave);

        UserAccount erin = new UserAccount(nextUserId(), "Erin Zhang", "erin@cityu.edu.hk", "City University");
        erin.setPassword(DEMO_PASSWORD);
        ReviewerRole erinReviewer = new ReviewerRole(erin);
        erinReviewer.addExpertiseTopic("Databases");
        erin.addRole(erinReviewer);
        saveUser(erin);

        UserAccount chairUser = new UserAccount(nextUserId(), "Prof. Lee", "lee@cuhk.edu.hk", "CUHK");
        chairUser.setPassword(DEMO_PASSWORD);
        chairUser.addRole(new ChairRole(chairUser));
        saveUser(chairUser);

        // One sample paper from Alice (City University) on ML.
        AuthorRole aliceAuthor = alice.getRole(AuthorRole.class);
        aliceAuthor.submitPaper(
                "Graph Neural Networks for Citation Recommendation",
                "We propose a graph neural network that recommends relevant citations "
                        + "by modelling the citation graph of a manuscript.",
                Arrays.asList("Machine Learning", "NLP"),
                Arrays.asList("City University"),
                this);
    }
}
