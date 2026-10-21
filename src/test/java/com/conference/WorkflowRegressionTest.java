package com.conference;

import com.conference.model.*;
import com.conference.persistence.*;
import com.conference.service.*;
import org.junit.Before;
import org.junit.Test;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.Assert.*;

/** Real business and file regression tests. Fixtures are retained; no directory deletion. */
public class WorkflowRegressionTest {
    private FileDataStore repo;
    private Path directory;
    private ChairRole chair;
    private AuthorRole author;
    private AssignmentService assignment;
    private ReviewService reviews;
    private ConferenceService conference;
    private int userSequence;

    @Before public void setup() throws Exception {
        Path base = Paths.get("target", "test-data");
        Files.createDirectories(base);
        directory = Files.createTempDirectory(base, "workflow-");
        repo = new FileDataStore(directory.resolve("state.ser").toFile());
        chair = repo.findUser("U006").getRole(ChairRole.class);
        author = repo.findUser("U001").getRole(AuthorRole.class);
        assignment = new AssignmentService(ConflictChecker.standard(), repo);
        reviews = new ReviewService(repo);
        conference = new ConferenceService(repo);
        assertTrue(author.withdrawPaper("P001", repo));
    }
    private ReviewerRole reviewer(String... topics) {
        UserAccount user = new UserAccount(repo.nextUserId(), "Reviewer", "reviewer" + (++userSequence) + "@example.org", "Independent " + userSequence);
        user.setPassword("password");
        ReviewerRole role = new ReviewerRole(user);
        role.setExpertiseTopics(Arrays.asList(topics));
        role.setMaxWorkload(repo.getConfiguration().getMaxWorkload());
        user.addRole(role);repo.saveUser(user);return role;
    }
    private Paper paper() {
        return author.submitPaper("Paper", "Abstract", Arrays.asList("NLP"), Arrays.asList("City University"), repo);
    }
    private void reviewPhase() { conference.changePhase(chair, ConferencePhase.REVIEW); }
    private void complete(Paper paper, ReviewerRole one, ReviewerRole two, int scoreOne, int scoreTwo) {
        assertTrue(chair.assignReviewer(paper, one, assignment));
        assertTrue(chair.assignReviewer(paper, two, assignment));
        reviews.submitReview(one, paper.getPaperId(), scoreOne, 3, "First review");
        reviews.submitReview(two, paper.getPaperId(), scoreTwo, 4, "Second review");
    }

    @Test public void roleTypesAreUniqueAndOwnershipIsChecked() {
        UserAccount user = repo.findUser("U001");
        user.addRole(new AuthorRole(user));assertEquals(1, user.getRoles().size());
        assertThrows(IllegalArgumentException.class, () -> user.addRole(new ChairRole(repo.findUser("U002"))));
        assertThrows(IllegalStateException.class, () -> conference.grantChair(new ChairRole(user), "U002"));
        conference.grantChair(chair, "U002");assertNotNull(repo.findUser("U002").getRole(ChairRole.class));
    }
    @Test public void emailLoginAndPasswordWork() {
        assertSame(repo.findUser("U001"), repo.findUser("ALICE@CITYU.EDU.HK"));
        assertTrue(repo.findUser("alice@cityu.edu.hk").checkPassword("123456"));
        assertFalse(repo.findUser("U001").checkPassword(null));
        assertFalse(repo.findUser("U001").checkPassword("wrong"));
    }
    @Test public void invalidUserFieldsAndDuplicateEmailsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new UserAccount("x", " ", "a@b.org", "X"));
        assertThrows(IllegalArgumentException.class, () -> new UserAccount("x", "X", "invalid", "X"));
        assertThrows(IllegalArgumentException.class, () -> new UserAccount("x", "X", "a@b.org", " "));
        assertThrows(IllegalArgumentException.class, () -> repo.saveUser(new UserAccount("x", "X", "ALICE@CITYU.EDU.HK", "X")));
    }
    @Test public void coauthorsAreStoredAndSubmittingAuthorAloneWithdraws() {
        Paper paper = author.submitPaper("Title", "Abstract", Arrays.asList("NLP"), Arrays.asList("Extra affiliation"), Arrays.asList("U002", "U002"), repo);
        assertEquals(Arrays.asList("U001", "U002"), paper.getAuthorIds());
        assertTrue(paper.getInstitutions().contains("HKU"));
        assertEquals(1, repo.findPapersByAuthor("U002").size());
        assertFalse(repo.findUser("U002").getRole(AuthorRole.class).withdrawPaper(paper.getPaperId(), repo));
        assertTrue(author.withdrawPaper(paper.getPaperId(), repo));
        assertFalse(author.withdrawPaper(paper.getPaperId(), repo));
    }
    @Test public void submissionValidatesMetadataAndUnknownCoauthorWithoutWriting() {
        int before = repo.findAllPapers().size();
        assertThrows(IllegalArgumentException.class, () -> author.submitPaper("Title", " ", Arrays.asList("NLP"), Arrays.asList("X"), repo));
        author = repo.findUser("U001").getRole(AuthorRole.class);
        assertThrows(IllegalArgumentException.class, () -> author.submitPaper("Title", "A", Collections.<String>emptyList(), Arrays.asList("X"), repo));
        author = repo.findUser("U001").getRole(AuthorRole.class);
        assertThrows(IllegalArgumentException.class, () -> author.submitPaper("Title", "A", Arrays.asList("NLP"), Arrays.asList("X"), Arrays.asList("unknown"), repo));
        assertEquals(before, repo.findAllPapers().size());
    }
    @Test public void configurationValidatesLimitsAndPersists() {
        assertThrows(IllegalArgumentException.class, () -> conference.configure(chair, 3, 3, 3, 2));
        assertThrows(IllegalArgumentException.class, () -> conference.configure(chair, 2, 0, 3, 2));
        assertThrows(IllegalArgumentException.class, () -> conference.configure(chair, 2, 3, Double.NaN, 2));
        conference.configure(chair, 1, 4, 3.5, 3);
        FileDataStore loaded = new FileDataStore(directory.resolve("state.ser").toFile());
        assertEquals(1, loaded.getConfiguration().getRequiredReviewers());
        assertEquals(4, loaded.findUser("U003").getRole(ReviewerRole.class).getMaxWorkload());
        assertEquals(3.5, loaded.getConfiguration().getAcceptanceThreshold(), 0.0);
    }
    @Test public void phaseGuardsAreEnforcedOutsideTheCui() {
        Paper paper = paper();ReviewerRole r = reviewer("NLP");
        assertThrows(IllegalStateException.class, () -> chair.assignReviewer(paper, r, assignment));
        assertThrows(IllegalStateException.class, () -> conference.changePhase(chair, ConferencePhase.DECISION));
        reviewPhase();
        assertThrows(IllegalStateException.class, () -> paper());
        assertThrows(IllegalStateException.class, () -> conference.configure(chair, 2, 3, 3, 2));
        assertThrows(IllegalStateException.class, () -> conference.changePhase(chair, ConferencePhase.SUBMISSION));
    }
    @Test public void automaticAssignmentUsesRelevanceAndStableTieBreaks() {
        Paper paper = paper();ReviewerRole irrelevant = reviewer("Quantum"), weak = reviewer("Other"), expert = reviewer("nlp"), expert2 = reviewer("NLP");
        reviewPhase();
        assertEquals(2, assignment.autoAssign(paper, Arrays.asList(irrelevant, weak, expert2, expert), chair));
        assertEquals(Arrays.asList(expert.getAccount().getUserId(), expert2.getAccount().getUserId()), paper.getAssignedReviewerIds());
        assertEquals(0, irrelevant.currentWorkload());
    }
    @Test public void automaticAssignmentBalancesWorkloadAndReportsShortfall() {
        Paper first = paper(), second = paper();ReviewerRole busy = reviewer("NLP"), free = reviewer("NLP"), free2 = reviewer("NLP");
        reviewPhase();assertTrue(chair.assignReviewer(first, busy, assignment));
        assertEquals(2, assignment.autoAssign(second, Arrays.asList(busy, free2, free), chair));
        assertFalse(second.getAssignedReviewerIds().contains(busy.getAccount().getUserId()));
        assertEquals(0, assignment.autoAssign(first, Arrays.asList(busy), chair));
        assertEquals(1, first.getAssignedReviewerIds().size());
    }
    @Test public void manualAndAutomaticAssignmentsNeverExceedTwo() {
        Paper paper = paper();ReviewerRole a = reviewer("NLP"), b = reviewer("NLP"), c = reviewer("NLP");reviewPhase();
        assertTrue(chair.assignReviewer(paper, a, assignment));assertTrue(chair.assignReviewer(paper, b, assignment));
        assertFalse(chair.assignReviewer(paper, c, assignment));
        assertEquals(0, assignment.autoAssign(paper, Arrays.asList(c), chair));
        assertFalse(chair.assignReviewer(paper, a, assignment));
        assertEquals(2, paper.getAssignedReviewerIds().size());assertEquals(0, c.currentWorkload());
    }
    @Test public void configuredOneReviewerLimitAlsoAppliesToManualAssignments() {
        conference.configure(chair, 1, 3, 3, 2);Paper paper = paper();ReviewerRole a = reviewer("NLP"), b = reviewer("NLP");reviewPhase();
        assertTrue(chair.assignReviewer(paper, a, assignment));assertFalse(chair.assignReviewer(paper, b, assignment));
    }
    @Test public void conflictsAndCapacityApplyToBothAssignmentRoutes() {
        Paper first = paper(), second = paper();ReviewerRole same = repo.findUser("U005").getRole(ReviewerRole.class);
        ReviewerRole declared = reviewer("NLP"), capacity = reviewer("NLP");capacity.setMaxWorkload(1);
        reviews.declareConflict(declared, first.getPaperId());reviewPhase();
        assertFalse(chair.assignReviewer(first, same, assignment));assertFalse(chair.assignReviewer(first, declared, assignment));
        assertTrue(chair.assignReviewer(first, capacity, assignment));assertFalse(chair.assignReviewer(second, capacity, assignment));
        assertEquals(0, assignment.autoAssign(first, Arrays.asList(same, declared, capacity), chair));
    }
    @Test public void authorIdentityCannotBeBypassedWithFalseAffiliation() {
        UserAccount alice = repo.findUser("U001");ReviewerRole self = new ReviewerRole(alice);self.setExpertiseTopics(Arrays.asList("NLP"));alice.addRole(self);repo.saveUser(alice);
        Paper paper = author.submitPaper("Own", "A", Arrays.asList("NLP"), Arrays.asList("Fake institution"), repo);reviewPhase();
        assertFalse(chair.assignReviewer(paper, self, assignment));assertNull(reviews.getAssignedPaperView(self, paper.getPaperId()));
    }
    @Test public void coauthorIdentityIsAlsoAConflict() {
        UserAccount bob = repo.findUser("U002");ReviewerRole self = new ReviewerRole(bob);self.setExpertiseTopics(Arrays.asList("NLP"));bob.addRole(self);repo.saveUser(bob);
        Paper paper = author.submitPaper("Own", "A", Arrays.asList("NLP"), Arrays.asList("Fake"), Arrays.asList("U002"), repo);reviewPhase();
        assertFalse(chair.assignReviewer(paper, self, assignment));
    }
    @Test public void expertiseMaintenanceChangesTheMatchingInputs() {
        ReviewerRole reviewer = reviewer("Quantum");Paper paper = paper();assertEquals(0, assignment.topicMatch(paper, reviewer));
        reviews.updateExpertise(reviewer, Arrays.asList("NLP", "nlp"));assertEquals(1, reviewer.getExpertiseTopics().size());
        assertEquals(1, assignment.topicMatch(paper, reviewer));
    }
    @Test public void reviewsRejectUnassignedDuplicateInvalidScoreAndBlankComment() {
        Paper paper = paper();ReviewerRole a = reviewer("NLP"), b = reviewer("NLP");reviewPhase();chair.assignReviewer(paper, a, assignment);
        assertNull(reviews.getAssignedPaperView(b, paper.getPaperId()));
        assertThrows(IllegalStateException.class, () -> reviews.submitReview(b, paper.getPaperId(), 3, 3, "x"));
        assertThrows(IllegalArgumentException.class, () -> reviews.submitReview(a, paper.getPaperId(), 0, 3, "x"));
        // Failed transaction restores repository identity; use refreshed roles.
        ReviewerRole refreshed = repo.findUser(a.getAccount().getUserId()).getRole(ReviewerRole.class);
        assertThrows(IllegalArgumentException.class, () -> reviews.submitReview(refreshed, paper.getPaperId(), 3, 6, "x"));
        ReviewerRole refreshed2 = repo.findUser(a.getAccount().getUserId()).getRole(ReviewerRole.class);
        assertThrows(IllegalArgumentException.class, () -> reviews.submitReview(refreshed2, paper.getPaperId(), 3, 3, " "));
        ReviewerRole refreshed3 = repo.findUser(a.getAccount().getUserId()).getRole(ReviewerRole.class);
        reviews.submitReview(refreshed3, paper.getPaperId(), 3, 3, "Valid");
        assertThrows(IllegalStateException.class, () -> reviews.submitReview(refreshed3, paper.getPaperId(), 3, 3, "Duplicate"));
        assertEquals(1, repo.findReviewsByPaper(paper.getPaperId()).size());
    }
    @Test public void withdrawalHidesPaperAndReviewHistoryAndReleasesBothSides() {
        Paper paper = paper();ReviewerRole a = reviewer("NLP"), b = reviewer("NLP");reviewPhase();complete(paper, a, b, 4, 4);
        assertTrue(author.withdrawPaper(paper.getPaperId(), repo));
        assertEquals(0, a.currentWorkload());assertEquals(0, b.currentWorkload());assertTrue(paper.getAssignedReviewerIds().isEmpty());
        assertNull(reviews.getAssignedPaperView(a, paper.getPaperId()));assertTrue(reviews.getReviewsByReviewer(a.getAccount().getUserId()).isEmpty());
        assertThrows(IllegalStateException.class, () -> reviews.submitReview(a, paper.getPaperId(), 3, 3, "x"));
        FileDataStore loaded = new FileDataStore(directory.resolve("state.ser").toFile());
        assertEquals(0, loaded.findUser(a.getAccount().getUserId()).getRole(ReviewerRole.class).currentWorkload());
        assertFalse(author.withdrawPaper(paper.getPaperId(), repo));
    }
    @Test public void lateConflictInvalidatesReviewAndAllowsReplacement() {
        Paper paper = paper();ReviewerRole a = reviewer("NLP"), b = reviewer("NLP"), c = reviewer("NLP");reviewPhase();complete(paper, a, b, 1, 5);
        reviews.declareConflict(a, paper.getPaperId());
        assertNull(reviews.getAssignedPaperView(a, paper.getPaperId()));assertEquals(0, a.currentWorkload());
        assertEquals(1, new DecisionService(repo).validReviews(paper).size());
        assertThrows(IllegalStateException.class, () -> reviews.submitReview(a, paper.getPaperId(), 4, 3, "x"));
        assertTrue(chair.assignReviewer(paper, c, assignment));reviews.submitReview(c, paper.getPaperId(), 5, 3, "Replacement");
        conference.changePhase(chair, ConferencePhase.DECISION);assertTrue(chair.makeDecision(paper, true, repo));
        assertEquals(3, new DecisionService(repo).authorFeedback(author, paper.getPaperId()).size());
    }
    @Test public void zeroAndIncompleteReviewsCannotEnterDecisionOrBeDecided() {
        Paper paper = paper();ReviewerRole a = reviewer("NLP");reviewPhase();chair.assignReviewer(paper, a, assignment);
        assertFalse(new DecisionService(repo).isReady(paper));
        assertThrows(IllegalStateException.class, () -> conference.changePhase(chair, ConferencePhase.DECISION));
        assertThrows(IllegalStateException.class, () -> chair.makeDecision(paper, true, repo));
        reviews.submitReview(a, paper.getPaperId(), 5, 3, "x");
        assertThrows(IllegalStateException.class, () -> conference.changePhase(chair, ConferencePhase.DECISION));
        assertEquals(PaperStatus.UNDER_REVIEW, paper.getStatus());
    }
    @Test public void summariesOverrideReasonAndAuthorFeedbackAreConsistent() {
        Paper paper = paper();ReviewerRole a = reviewer("NLP"), b = reviewer("NLP");reviewPhase();complete(paper, a, b, 1, 5);
        DecisionService decisions = new DecisionService(repo);
        assertEquals(3.0, decisions.average(paper), 0.0);assertTrue(decisions.hasDisagreement(paper));assertTrue(decisions.recommendsAccept(paper));
        assertThrows(IllegalStateException.class, () -> decisions.authorFeedback(author, paper.getPaperId()));
        conference.changePhase(chair, ConferencePhase.DECISION);
        assertThrows(IllegalArgumentException.class, () -> chair.makeDecision(paper, false, " ", repo));
        assertTrue(chair.makeDecision(paper, false, "Insufficient contribution", repo));
        assertFalse(chair.makeDecision(paper, true, repo));assertFalse(author.withdrawPaper(paper.getPaperId(), repo));
        List<String> feedback = decisions.authorFeedback(author, paper.getPaperId());
        assertEquals(3, feedback.size());assertTrue(feedback.get(1).contains("First review"));
        for (String text : feedback) { assertFalse(text.contains(a.getAccount().getUserId()));assertFalse(text.contains("reviewer=")); }
        assertThrows(IllegalStateException.class, () -> decisions.authorFeedback(repo.findUser("U002").getRole(AuthorRole.class), paper.getPaperId()));
        FileDataStore loaded = new FileDataStore(directory.resolve("state.ser").toFile());
        assertEquals("Insufficient contribution", loaded.findPaper(paper.getPaperId()).getOverrideReason());
        assertTrue(loaded.findAuditEvents().stream().anyMatch(e -> "DECISION_OVERRIDE".equals(e.getAction())));
        assertEquals(ConferencePhase.DECISION, loaded.getConfiguration().getPhase());
    }
    @Test public void disagreementThresholdIsInclusiveAndRecommendationThresholdIsInclusive() {
        conference.configure(chair, 2, 3, 3.0, 2);Paper paper = paper();ReviewerRole a = reviewer("NLP"), b = reviewer("NLP");reviewPhase();complete(paper, a, b, 2, 4);
        assertTrue(new DecisionService(repo).hasDisagreement(paper));assertTrue(new DecisionService(repo).recommendsAccept(paper));
    }
    @Test public void malformedAndNullSnapshotsAreNeverOverwritten() throws Exception {
        for (int i = 0; i < 3; i++) {
            Path file = directory.resolve("invalid" + i + ".ser");
            if (i == 0) Files.write(file, new byte[] {1, 2, 3});
            else try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file.toFile()))) { out.writeObject(i == 1 ? null : "wrong-type"); }
            byte[] before = Files.readAllBytes(file);
            assertThrows(StorageException.class, () -> new FileDataStore(file.toFile()));
            assertArrayEquals(before, Files.readAllBytes(file));
        }
    }
    @Test public void obstructedPathFailsInsteadOfReportingFirstRunSuccess() throws Exception {
        Path obstruction = directory.resolve("not-a-directory");Files.write(obstruction, new byte[] {1});
        assertThrows(StorageException.class, () -> new FileDataStore(obstruction.resolve("state.ser").toFile()));
        assertArrayEquals(new byte[] {1}, Files.readAllBytes(obstruction));
    }
    @Test public void previousSnapshotBackupIsReadable() throws Exception {
        paper();Path backup = directory.resolve("state.ser.bak");assertTrue(Files.exists(backup));
        FileDataStore previous = new FileDataStore(backup.toFile());assertNotNull(previous.findUser("U001"));
    }
    @Test public void transactionFailureRollsBackDataAndAudit() {
        int count = repo.findAllUsers().size(), events = repo.findAuditEvents().size();
        assertThrows(IllegalArgumentException.class, () -> repo.transaction(() -> {
            UserAccount user = new UserAccount(repo.nextUserId(), "Transient", "transient@example.org", "X");user.addRole(new AuthorRole(user));repo.saveUser(user);
            repo.recordAudit(new AuditEvent("TEST", user.getUserId(), "-", "must rollback"));
            throw new IllegalArgumentException("injected failure");
        }));
        assertEquals(count, repo.findAllUsers().size());assertEquals(events, repo.findAuditEvents().size());assertNull(repo.findUser("transient@example.org"));
        FileDataStore loaded = new FileDataStore(directory.resolve("state.ser").toFile());assertNull(loaded.findUser("transient@example.org"));
    }
    @Test public void writeFailureRollsBackAndPreservesCommittedBytes() throws Exception {
        FailingStore failing = new FailingStore(directory.resolve("failing.ser").toFile());
        byte[] before = Files.readAllBytes(directory.resolve("failing.ser"));int count = failing.findAllUsers().size();failing.fail = true;
        assertThrows(StorageException.class, () -> failing.saveUser(new UserAccount("U999", "New", "new@example.org", "X")));
        assertEquals(count, failing.findAllUsers().size());assertArrayEquals(before, Files.readAllBytes(directory.resolve("failing.ser")));
    }

    @Test public void submittedReviewReleasesCapacityWithoutRemovingAssignmentOrHistory() {
        Paper first = paper(), second = paper();
        ReviewerRole reviewer = reviewer("NLP");reviewer.setMaxWorkload(1);reviewPhase();
        assertTrue(chair.assignReviewer(first, reviewer, assignment));
        assertEquals(1, reviewer.currentWorkload());assertFalse(reviewer.hasCapacity());
        assertFalse(chair.assignReviewer(second, reviewer, assignment));
        reviews.submitReview(reviewer, first.getPaperId(), 4, 3, "Completed review");
        assertEquals(0, reviewer.currentWorkload());assertTrue(reviewer.hasCapacity());
        assertTrue(reviewer.isAssignedTo(first.getPaperId()));
        assertTrue(first.getAssignedReviewerIds().contains(reviewer.getAccount().getUserId()));
        assertNotNull(reviews.getAssignedPaperView(reviewer, first.getPaperId()));
        assertEquals(1, reviews.getReviewsByReviewer(reviewer.getAccount().getUserId()).size());
        assertEquals(1, new DecisionService(repo).validReviews(first).size());
        assertTrue(chair.assignReviewer(second, reviewer, assignment));assertEquals(1, reviewer.currentWorkload());
        assertThrows(IllegalStateException.class, () -> reviews.submitReview(reviewer, first.getPaperId(), 5, 3, "Duplicate"));
        assertEquals(1, reviewer.currentWorkload());
        FileDataStore loaded = new FileDataStore(directory.resolve("state.ser").toFile());
        ReviewerRole restored = loaded.findUser(reviewer.getAccount().getUserId()).getRole(ReviewerRole.class);
        assertEquals(1, restored.currentWorkload());assertEquals(2, restored.getAssignedPaperIds().size());
        assertTrue(author.withdrawPaper(first.getPaperId(), repo));
        assertEquals(1, reviewer.currentWorkload()); // completed withdrawal must not free the other pending task
        assertTrue(author.withdrawPaper(second.getPaperId(), repo));assertEquals(0, reviewer.currentWorkload());
    }

    @Test public void restartDerivesAssignmentsAndCompletionWithoutSerializedReviewerCaches() throws Exception {
        Paper paper = paper();ReviewerRole reviewer = reviewer("NLP");reviewPhase();
        assertTrue(chair.assignReviewer(paper, reviewer, assignment));
        reviews.submitReview(reviewer, paper.getPaperId(), 4, 3, "Completed before upgrade");
        Set<String> fields = new HashSet<String>();
        for (ObjectStreamField field : ObjectStreamClass.lookup(ReviewerRole.class).getFields()) fields.add(field.getName());
        assertFalse(fields.contains("assignedPaperIds"));
        assertFalse(fields.contains("completedPaperIds"));
        assertFalse(fields.contains("repository"));
        assertEquals(Arrays.asList(paper.getPaperId()), reviewer.getAssignedPaperIds());
        assertThrows(UnsupportedOperationException.class, () -> reviewer.getAssignedPaperIds().add("P999"));
        repo.persist();
        FileDataStore loaded = new FileDataStore(directory.resolve("state.ser").toFile());
        ReviewerRole restored = loaded.findUser(reviewer.getAccount().getUserId()).getRole(ReviewerRole.class);
        assertEquals(0, restored.currentWorkload());assertTrue(restored.isAssignedTo(paper.getPaperId()));
        assertEquals(1, loaded.findReviewsByPaper(paper.getPaperId()).size());
    }

    @Test public void paperAssignmentsAreTheOnlySourceOfReverseNavigation() {
        Paper paper = paper();ReviewerRole reviewer = reviewer("NLP");reviewPhase();
        paper.addAssignedReviewer(reviewer.getAccount().getUserId());repo.savePaper(paper);
        assertTrue(reviewer.isAssignedTo(paper.getPaperId()));assertEquals(1, reviewer.currentWorkload());
        paper.removeAssignedReviewer(reviewer.getAccount().getUserId());repo.savePaper(paper);
        assertFalse(reviewer.isAssignedTo(paper.getPaperId()));assertEquals(0, reviewer.currentWorkload());
        FileDataStore loaded = new FileDataStore(directory.resolve("state.ser").toFile());
        ReviewerRole restored = loaded.findUser(reviewer.getAccount().getUserId()).getRole(ReviewerRole.class);
        assertTrue(restored.getAssignedPaperIds().isEmpty());assertEquals(0, restored.currentWorkload());
    }

    @Test public void rejectedReviewDoesNotReleasePendingWorkload() {
        Paper paper = paper();ReviewerRole reviewer = reviewer("NLP");reviewPhase();
        assertTrue(chair.assignReviewer(paper, reviewer, assignment));
        assertThrows(IllegalArgumentException.class, () -> reviews.submitReview(reviewer, paper.getPaperId(), 0, 3, "Invalid"));
        ReviewerRole restored = repo.findUser(reviewer.getAccount().getUserId()).getRole(ReviewerRole.class);
        assertEquals(1, restored.currentWorkload());assertTrue(repo.findReviewsByPaper(paper.getPaperId()).isEmpty());
    }

    @Test public void failedReviewSaveRollsBackCompletionAndRetainsPendingWorkload() throws Exception {
        FailingStore failing = new FailingStore(directory.resolve("review-failure.ser").toFile());
        ChairRole actor = failing.findUser("U006").getRole(ChairRole.class);
        ReviewerRole reviewer = failing.findUser("U003").getRole(ReviewerRole.class);
        new ConferenceService(failing).changePhase(actor, ConferencePhase.REVIEW);
        assertTrue(actor.assignReviewer(failing.findPaper("P001"), reviewer, new AssignmentService(ConflictChecker.standard(), failing)));
        byte[] before = Files.readAllBytes(directory.resolve("review-failure.ser"));failing.fail = true;
        assertThrows(StorageException.class, () -> new ReviewService(failing).submitReview(reviewer, "P001", 4, 3, "Save failure"));
        ReviewerRole restored = failing.findUser("U003").getRole(ReviewerRole.class);
        assertEquals(1, restored.currentWorkload());assertTrue(restored.isAssignedTo("P001"));
        assertTrue(failing.findReviewsByPaper("P001").isEmpty());
        assertArrayEquals(before, Files.readAllBytes(directory.resolve("review-failure.ser")));
    }

    private static class FailingStore extends FileDataStore {
        boolean fail;
        FailingStore(File file) { super(file); }
        @Override public synchronized void persist() {
            if (fail) throw new StorageException("Injected storage failure", new IOException("fixture"));
            super.persist();
        }
    }
}
