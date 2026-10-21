package com.conference.persistence;

import com.conference.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Atomic file snapshots. Corrupt files are preserved; storage failure is fatal to a session. */
public class FileDataStore implements ConferenceRepository {
    private final File storageFile;
    private DataStore store;
    private int transactionDepth;
    public static final String DEMO_PASSWORD = "123456";

    public FileDataStore() { this(new File("data", "conference-data.ser")); }
    public FileDataStore(File storageFile) {
        this.storageFile = storageFile;
        load();
    }

    private void load() {
        if (storageFile.exists()) {
            try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(storageFile))) {
                Object loaded = in.readObject();
                if (!(loaded instanceof DataStore)) throw new IOException("Invalid snapshot type or null snapshot");
                store = (DataStore) loaded;
                migrateAndValidate();
                System.out.println("[FileDataStore] Loaded existing data from " + storageFile.getPath());
                return;
            } catch (IOException | ClassNotFoundException | RuntimeException e) {
                throw new StorageException("Could not read data file. Original file was preserved: " + storageFile.getPath(), e);
            }
        }
        store = new DataStore();
        transactionDepth++;
        try { seedDemoData(); } finally { transactionDepth--; }
        persist();
        System.out.println("[FileDataStore] First run: created demo data in " + storageFile.getPath());
        System.out.println("[FileDataStore] Demo users U001-U006, password for all: " + DEMO_PASSWORD);
    }

    private void migrateAndValidate() throws IOException {
        if (store.users == null || store.papers == null || store.reviews == null)
            throw new IOException("Missing snapshot collections");
        boolean legacy = store.configuration == null;
        if (legacy) store.configuration = new ConferenceConfiguration();
        if (store.auditEvents == null) store.auditEvents = new ArrayList<AuditEvent>();
        Set<String> emails = new HashSet<String>();
        for (Map.Entry<String, UserAccount> entry : store.users.entrySet()) {
            UserAccount u = entry.getValue();
            if (u == null || !entry.getKey().equals(u.getUserId()) || u.getEmail() == null || u.getRoles() == null)
                throw new IOException("Invalid user record");
            if (!emails.add(u.getEmail().toLowerCase(Locale.ROOT))) throw new IOException("Duplicate email");
            Set<Class<?>> types = new HashSet<Class<?>>();
            for (ConferenceRole role : u.getRoles())
                if (role == null || role.getAccount() != u || !types.add(role.getClass()))
                    throw new IOException("Invalid or duplicate account roles");
        }
        attachReviewers();
        boolean assigned = false, undecided = false;
        for (Map.Entry<String, Paper> entry : store.papers.entrySet()) {
            Paper paper = entry.getValue();
            if (paper == null || !entry.getKey().equals(paper.getPaperId()) || paper.getStatus() == null
                    || paper.getAuthorIds().isEmpty() || paper.getTopics().isEmpty()) throw new IOException("Invalid paper record");
            for (String id : paper.getAuthorIds()) if (store.users.get(id) == null) throw new IOException("Unknown author");
            if (paper.getAssignedReviewerIds().size() > 2) throw new IOException("Paper exceeds two-reviewer limit");
            if (paper.getStatus() == PaperStatus.WITHDRAWN) {
                for (String id : new ArrayList<String>(paper.getAssignedReviewerIds())) {
                    paper.removeAssignedReviewer(id);
                }
            }
            for (String id : paper.getAssignedReviewerIds()) {
                UserAccount u = store.users.get(id);
                if (u == null || u.getRole(ReviewerRole.class) == null) throw new IOException("Unknown reviewer");
            }
            assigned |= paper.getStatus() == PaperStatus.UNDER_REVIEW;
            undecided |= paper.getStatus() == PaperStatus.SUBMITTED || paper.getStatus() == PaperStatus.UNDER_REVIEW;
        }
        Set<String> pairs = new HashSet<String>();
        for (Review review : store.reviews.values()) {
            if (review == null || !store.papers.containsKey(review.getPaperId()) || !store.users.containsKey(review.getReviewerId())
                    || !pairs.add(review.getPaperId() + ":" + review.getReviewerId())) throw new IOException("Invalid or duplicate review");
        }
        // Legacy reviewer-side assignment/completion fields are ignored by deserialization.
        // Reverse navigation and workload now query Paper and Review without rebuilding caches.
        if (legacy) {
            ConferencePhase phase = assigned ? ConferencePhase.REVIEW : (!undecided && !store.papers.isEmpty() ? ConferencePhase.DECISION : ConferencePhase.SUBMISSION);
            store.configuration = store.configuration.withPhase(phase);
            store.auditEvents.add(new AuditEvent("LEGACY_MIGRATION", "SYSTEM", "-", "Added configuration and audit support; no historical events fabricated."));
        }
    }

    private void attachReviewers() {
        for (UserAccount user : store.users.values()) {
            ReviewerRole reviewer = user.getRole(ReviewerRole.class);
            if (reviewer != null) reviewer.attachRepository(this);
        }
    }

    @Override public synchronized void persist() {
        if (transactionDepth > 0) return;
        Path target = storageFile.toPath().toAbsolutePath();
        try {
            Path parent = target.getParent();
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, "conference-snapshot-", ".tmp");
            try (FileOutputStream file = new FileOutputStream(temp.toFile()); ObjectOutputStream out = new ObjectOutputStream(file)) {
                out.writeObject(store);
                out.flush();
                file.getFD().sync();
            }
            // Keep the previously committed snapshot. A failed write never truncates it.
            if (Files.exists(target)) Files.copy(target, target.resolveSibling(target.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            // Fail safely if this filesystem cannot atomically replace the snapshot.
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Failed to save data; operation was not confirmed. Previous snapshot was preserved: " + storageFile.getPath(), e);
        }
    }

    private DataStore copySnapshot() {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream out = new ObjectOutputStream(bytes)) { out.writeObject(store); }
            try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { return (DataStore) in.readObject(); }
        } catch (IOException | ClassNotFoundException e) { throw new StorageException("Could not prepare transaction.", e); }
    }

    @Override public synchronized void transaction(Runnable action) {
        if (transactionDepth > 0) { action.run(); return; }
        DataStore before = copySnapshot();
        transactionDepth++;
        try {
            action.run();
            transactionDepth--;
            persist();
        } catch (RuntimeException e) {
            transactionDepth = 0;
            store = before;
            attachReviewers();
            throw e;
        }
    }

    @Override public synchronized String nextUserId() { return String.format("U%03d", ++store.userSeq); }
    @Override public synchronized String nextPaperId() { return String.format("P%03d", ++store.paperSeq); }
    @Override public synchronized String nextReviewId() { return String.format("R%03d", ++store.reviewSeq); }

    @Override public synchronized void saveUser(final UserAccount user) {
        for (UserAccount other : store.users.values())
            if (!other.getUserId().equals(user.getUserId()) && other.getEmail().equalsIgnoreCase(user.getEmail()))
                throw new IllegalArgumentException("Email is already registered.");
        transaction(() -> {
            ReviewerRole reviewer = user.getRole(ReviewerRole.class);
            if (reviewer != null) reviewer.attachRepository(this);
            store.users.put(user.getUserId(), user);
        });
    }
    /** Supports existing IDs and case-insensitive email login without changing callers. */
    @Override public synchronized UserAccount findUser(String id) {
        if (id == null) return null;
        String key = id.trim();
        UserAccount user = store.users.get(key.toUpperCase(Locale.ROOT));
        if (user != null) return user;
        for (UserAccount candidate : store.users.values()) if (candidate.getEmail().equalsIgnoreCase(key)) return candidate;
        return null;
    }
    @Override public synchronized List<UserAccount> findAllUsers() { return new ArrayList<UserAccount>(store.users.values()); }
    @Override public synchronized List<ReviewerRole> findAllReviewers() {
        List<ReviewerRole> result = new ArrayList<ReviewerRole>();
        for (UserAccount user : store.users.values()) {
            ReviewerRole role = user.getRole(ReviewerRole.class);
            if (role != null) result.add(role);
        }
        return result;
    }
    @Override public synchronized void savePaper(final Paper paper) { transaction(() -> store.papers.put(paper.getPaperId(), paper)); }
    @Override public synchronized Paper findPaper(String id) { return id == null ? null : store.papers.get(id.trim().toUpperCase(Locale.ROOT)); }
    @Override public synchronized List<Paper> findAllPapers() { return new ArrayList<Paper>(store.papers.values()); }
    @Override public synchronized List<Paper> findPapersByAuthor(String authorId) {
        List<Paper> result = new ArrayList<Paper>();
        for (Paper paper : store.papers.values()) if (paper.getAuthorIds().contains(authorId)) result.add(paper);
        return result;
    }
    @Override public synchronized void saveReview(final Review review) { transaction(() -> store.reviews.put(review.getReviewId(), review)); }
    @Override public synchronized List<Review> findReviewsByPaper(String id) {
        List<Review> result = new ArrayList<Review>();
        for (Review review : store.reviews.values()) if (review.getPaperId().equals(id)) result.add(review);
        return result;
    }
    @Override public synchronized List<Review> findReviewsByReviewer(String id) {
        List<Review> result = new ArrayList<Review>();
        for (Review review : store.reviews.values()) if (review.getReviewerId().equals(id)) result.add(review);
        return result;
    }
    @Override public synchronized ConferenceConfiguration getConfiguration() { return store.configuration; }
    @Override public synchronized void saveConfiguration(final ConferenceConfiguration configuration) {
        if (configuration == null) throw new IllegalArgumentException("Configuration is required.");
        transaction(() -> store.configuration = configuration);
    }
    @Override public synchronized void recordAudit(final AuditEvent event) {
        if (event == null) throw new IllegalArgumentException("Audit event is required.");
        transaction(() -> store.auditEvents.add(event));
    }
    @Override public synchronized List<AuditEvent> findAuditEvents() { return new ArrayList<AuditEvent>(store.auditEvents); }

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
