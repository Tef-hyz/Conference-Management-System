package com.conference.ui;

import com.conference.model.AnonymousPaperView;
import com.conference.model.AuthorRole;
import com.conference.model.ChairRole;
import com.conference.model.ConferenceRole;
import com.conference.model.Paper;
import com.conference.model.Review;
import com.conference.model.ReviewerRole;
import com.conference.model.UserAccount;
import com.conference.persistence.ConferenceRepository;
import com.conference.service.AssignmentService;
import com.conference.service.ConflictChecker;
import com.conference.service.DeclaredConflictRule;
import com.conference.service.ReviewService;
import com.conference.service.SameInstitutionRule;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Character-based user interface (CUI) of the conference paper review system.
 * All input is validated; invalid input never crashes the program.
 */
public class ConsoleApp {

    private final Scanner scanner = new Scanner(System.in);
    private final ConferenceRepository repo;
    private final ConflictChecker conflictChecker;
    private final AssignmentService assignmentService;
    private final ReviewService reviewService;

    private UserAccount currentUser;

    public ConsoleApp(ConferenceRepository repo) {
        this.repo = repo;
        this.conflictChecker = new ConflictChecker();
        conflictChecker.addRule(new SameInstitutionRule());
        conflictChecker.addRule(new DeclaredConflictRule());
        this.assignmentService = new AssignmentService(conflictChecker, repo);
        this.reviewService = new ReviewService(repo);
    }

    // ==================================================================
    // Main loop
    // ==================================================================

    public void run() {
        printBanner();
        boolean running = true;
        while (running) {
            if (currentUser == null) {
                running = mainMenu();
            } else {
                sessionMenu();
            }
        }
        repo.persist();
        System.out.println("Data saved. Goodbye!");
    }

    private void printBanner() {
        System.out.println("======================================================");
        System.out.println("      Conference Paper Review System (CUI) v1.0");
        System.out.println("======================================================");
    }

    private boolean mainMenu() {
        System.out.println();
        System.out.println("--- Main Menu ---");
        System.out.println("1. Login");
        System.out.println("2. Register a new user");
        System.out.println("0. Exit");
        int choice = readInt("Choose an option: ", 0, 2);
        switch (choice) {
            case 1:
                login();
                break;
            case 2:
                registerUser();
                break;
            default:
                return false;
        }
        return true;
    }

    // ==================================================================
    // Authentication / registration
    // ==================================================================

    private void login() {
        String id = readLine("User ID: ").trim();
        String password = readLine("Password: ");
        UserAccount user = repo.findUser(id);
        if (user == null || !user.hasPassword() || !user.checkPassword(password)) {
            System.out.println("Invalid user ID or password.");
            return;
        }
        currentUser = user;
        System.out.println("Logged in as " + user.getName() + " (" + user.getUserId() + ").");
    }

    private void registerUser() {
        String name = readLine("Name: ");
        String email = readLine("Email: ");
        String institution = readLine("Institution: ");
        String password = readLine("Password: ");
        UserAccount user = new UserAccount(repo.nextUserId(), name, email, institution);
        try {
            user.setPassword(password);
        } catch (IllegalArgumentException e) {
            System.out.println("Registration failed: " + e.getMessage());
            return;
        }
        grantRoleMenu(user);
        repo.saveUser(user);
        System.out.println("Registered: " + user);
    }

    private void grantRoleMenu(UserAccount user) {
        System.out.println("Grant roles to " + user.getName() + ":");
        System.out.println("1. Author   2. Reviewer   3. Chair   (comma-separated, e.g. 1,2)");
        String input = readLine("Roles: ");
        for (String token : input.split(",")) {
            String t = token.trim();
            if ("1".equals(t)) {
                user.addRole(new AuthorRole(user));
            } else if ("2".equals(t)) {
                ReviewerRole reviewer = new ReviewerRole(user);
                String topics = readLine("Expertise topics (comma-separated): ");
                for (String topic : topics.split(",")) {
                    reviewer.addExpertiseTopic(topic);
                }
                user.addRole(reviewer);
            } else if ("3".equals(t)) {
                user.addRole(new ChairRole(user));
            }
        }
        if (user.getRoles().isEmpty()) {
            System.out.println("No valid role selected; defaulting to Author.");
            user.addRole(new AuthorRole(user));
        }
    }

    // ==================================================================
    // Session (role selection + role menus)
    // ==================================================================

    private void sessionMenu() {
        List<ConferenceRole> roles = new ArrayList<ConferenceRole>(currentUser.getRoles());
        System.out.println();
        System.out.println("--- " + currentUser.getName() + ": choose a role ---");
        for (int i = 0; i < roles.size(); i++) {
            System.out.println((i + 1) + ". " + roles.get(i).getRoleName());
        }
        System.out.println("0. Logout");
        int choice = readInt("Choose an option: ", 0, roles.size());
        if (choice == 0) {
            currentUser = null;
            return;
        }
        ConferenceRole role = roles.get(choice - 1);
        if (role instanceof AuthorRole) {
            authorMenu((AuthorRole) role);
        } else if (role instanceof ReviewerRole) {
            reviewerMenu((ReviewerRole) role);
        } else if (role instanceof ChairRole) {
            chairMenu((ChairRole) role);
        }
    }

    // ==================================================================
    // Input helpers (robust against bad input)
    // ==================================================================

    private String readLine(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine();
    }

    private int readInt(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                int value = Integer.parseInt(input);
                if (value < min || value > max) {
                    System.out.println("Please enter a number between " + min + " and " + max + ".");
                    continue;
                }
                return value;
            } catch (NumberFormatException e) {
                System.out.println("Invalid number, please try again.");
            }
        }
    }

    private List<String> readCsv(String prompt) {
        String input = readLine(prompt);
        List<String> result = new ArrayList<String>();
        for (String token : input.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }
    // ==================================================================
    // Author menu
    // ==================================================================

    private void authorMenu(AuthorRole author) {
        boolean back = false;
        while (!back && currentUser != null) {
            System.out.println();
            System.out.println("--- Author Menu ---");
            System.out.println("1. Submit a paper");
            System.out.println("2. Withdraw a paper");
            System.out.println("3. List my papers");
            System.out.println("0. Back (switch role)");
            int choice = readInt("Choose an option: ", 0, 3);
            switch (choice) {
                case 1:
                    submitPaper(author);
                    break;
                case 2:
                    withdrawPaper(author);
                    break;
                case 3:
                    listMyPapers(author);
                    break;
                default:
                    back = true;
            }
        }
    }

    private void submitPaper(AuthorRole author) {
        String title = readLine("Paper title: ");
        String abstractText = readLine("Abstract: ");
        List<String> topics = readCsv("Topics (comma-separated): ");
        List<String> institutions = readCsv("Author institutions (comma-separated): ");
        try {
            Paper paper = author.submitPaper(title, abstractText, topics, institutions, repo);
            System.out.println("Paper submitted: " + paper);
        } catch (IllegalArgumentException e) {
            System.out.println("Submission failed: " + e.getMessage());
        }
    }

    private void withdrawPaper(AuthorRole author) {
        listMyPapers(author);
        String paperId = readLine("Enter paper ID to withdraw: ");
        if (author.withdrawPaper(paperId.trim(), repo)) {
            System.out.println("Paper " + paperId + " withdrawn.");
        } else {
            System.out.println("Could not withdraw (not your paper, unknown ID, or already decided).");
        }
    }

    private void listMyPapers(AuthorRole author) {
        List<Paper> papers = repo.findPapersByAuthor(author.getAccount().getUserId());
        if (papers.isEmpty()) {
            System.out.println("You have not submitted any papers yet.");
            return;
        }
        System.out.println("--- My Papers ---");
        for (Paper p : papers) {
            System.out.println("  " + p);
        }
    }

    // ==================================================================
    // Reviewer menu
    // ==================================================================

    private void reviewerMenu(ReviewerRole reviewer) {
        boolean back = false;
        while (!back && currentUser != null) {
            System.out.println();
            System.out.println("--- Reviewer Menu --- (workload: " + reviewer.currentWorkload()
                    + "/" + reviewer.getMaxWorkload() + ")");
            System.out.println("1. View my assigned papers (anonymous)");
            System.out.println("2. Submit a review");
            System.out.println("3. Declare a conflict of interest");
            System.out.println("4. List my submitted reviews");
            System.out.println("0. Back (switch role)");
            int choice = readInt("Choose an option: ", 0, 4);
            switch (choice) {
                case 1:
                    viewAssignedPapers(reviewer);
                    break;
                case 2:
                    submitReview(reviewer);
                    break;
                case 3:
                    declareConflict(reviewer);
                    break;
                case 4:
                    listMyReviews(reviewer);
                    break;
                default:
                    back = true;
            }
        }
    }

    private void viewAssignedPapers(ReviewerRole reviewer) {
        List<String> ids = reviewer.getAssignedPaperIds();
        if (ids.isEmpty()) {
            System.out.println("No papers assigned to you yet.");
            return;
        }
        System.out.println("--- Assigned Papers (anonymous view) ---");
        for (String paperId : ids) {
            AnonymousPaperView view = reviewService.getAssignedPaperView(reviewer, paperId);
            if (view != null) {
                System.out.println(view);
            }
        }
    }

    private void submitReview(ReviewerRole reviewer) {
        viewAssignedPapers(reviewer);
        if (reviewer.getAssignedPaperIds().isEmpty()) {
            return;
        }
        String paperId = readLine("Paper ID to review: ").trim();
        int score = readInt("Score (1=reject ... 5=strong accept): ", Review.MIN_SCORE, Review.MAX_SCORE);
        int confidence = readInt("Confidence (1=low ... 5=high): ", Review.MIN_SCORE, Review.MAX_SCORE);
        String comments = readLine("Comments: ");
        try {
            Review review = reviewService.submitReview(reviewer, paperId, score, confidence, comments);
            System.out.println("Review submitted: " + review);
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Review rejected: " + e.getMessage());
        }
    }

    private void declareConflict(ReviewerRole reviewer) {
        String id = readLine("Enter the paper ID or author user ID you have a conflict with: ");
        reviewer.declareConflict(id);
        repo.saveUser(reviewer.getAccount());
        System.out.println("Conflict declared for: " + id);
    }

    private void listMyReviews(ReviewerRole reviewer) {
        List<Review> reviews = reviewService.getReviewsByReviewer(reviewer.getAccount().getUserId());
        if (reviews.isEmpty()) {
            System.out.println("You have not submitted any reviews yet.");
            return;
        }
        System.out.println("--- My Reviews ---");
        for (Review r : reviews) {
            System.out.println("  " + r);
        }
    }

    // ==================================================================
    // Chair menu
    // ==================================================================

    private void chairMenu(ChairRole chair) {
        boolean back = false;
        while (!back && currentUser != null) {
            System.out.println();
            System.out.println("--- Chair Menu ---");
            System.out.println("1. List all papers");
            System.out.println("2. Auto-assign reviewers to a paper");
            System.out.println("3. Manually assign a reviewer");
            System.out.println("4. View reviews of a paper");
            System.out.println("5. Make decision (accept / reject)");
            System.out.println("0. Back (switch role)");
            int choice = readInt("Choose an option: ", 0, 5);
            switch (choice) {
                case 1:
                    listAllPapers();
                    break;
                case 2:
                    autoAssign(chair);
                    break;
                case 3:
                    manualAssign(chair);
                    break;
                case 4:
                    viewReviews();
                    break;
                case 5:
                    makeDecision(chair);
                    break;
                default:
                    back = true;
            }
        }
    }

    private void listAllPapers() {
        List<Paper> papers = repo.findAllPapers();
        if (papers.isEmpty()) {
            System.out.println("No papers in the system.");
            return;
        }
        System.out.println("--- All Papers ---");
        for (Paper p : papers) {
            int reviewCount = reviewService.getReviewsForPaper(p.getPaperId()).size();
            System.out.println("  " + p + " | reviews received=" + reviewCount);
        }
    }

    private void autoAssign(ChairRole chair) {
        listAllPapers();
        String paperId = readLine("Paper ID to auto-assign: ").trim();
        Paper paper = repo.findPaper(paperId);
        if (paper == null) {
            System.out.println("Paper not found.");
            return;
        }
        int assigned = assignmentService.autoAssign(paper, repo.findAllReviewers());
        System.out.println("Auto-assignment complete: " + assigned + " reviewer(s) assigned. "
                + "Current reviewers: " + paper.getAssignedReviewerIds());
    }

    private void manualAssign(ChairRole chair) {
        listAllPapers();
        String paperId = readLine("Paper ID: ").trim();
        Paper paper = repo.findPaper(paperId);
        if (paper == null) {
            System.out.println("Paper not found.");
            return;
        }
        List<ReviewerRole> reviewers = repo.findAllReviewers();
        if (reviewers.isEmpty()) {
            System.out.println("No reviewers registered in the system.");
            return;
        }
        System.out.println("--- Available Reviewers ---");
        for (ReviewerRole r : reviewers) {
            List<String> violated = conflictChecker.findViolatedRules(r, paper);
            String note = violated.isEmpty() ? "eligible" : "CONFLICT: " + violated;
            System.out.println("  " + r.getAccount().getUserId() + " " + r.getAccount().getName()
                    + " | expertise=" + r.getExpertiseTopics()
                    + " | workload=" + r.currentWorkload() + "/" + r.getMaxWorkload()
                    + " | " + note);
        }
        String reviewerId = readLine("Reviewer user ID to assign: ").trim();
        UserAccount user = repo.findUser(reviewerId);
        ReviewerRole reviewer = user == null ? null : user.getRole(ReviewerRole.class);
        if (reviewer == null) {
            System.out.println("That user does not exist or is not a reviewer.");
            return;
        }
        if (chair.assignReviewer(paper, reviewer, assignmentService)) {
            System.out.println("Reviewer " + reviewerId + " assigned to " + paperId + ".");
        } else {
            System.out.println("Assignment refused (conflict of interest, workload limit, "
                    + "duplicate assignment, or paper not assignable).");
        }
    }

    private void viewReviews() {
        listAllPapers();
        String paperId = readLine("Paper ID: ").trim();
        List<Review> reviews = reviewService.getReviewsForPaper(paperId);
        if (reviews.isEmpty()) {
            System.out.println("No reviews for " + paperId + " yet.");
            return;
        }
        System.out.println("--- Reviews for " + paperId + " ---");
        double total = 0;
        for (Review r : reviews) {
            System.out.println("  " + r);
            total += r.getScore();
        }
        System.out.printf("  Average score: %.2f (%d review(s))%n", total / reviews.size(), reviews.size());
    }

    private void makeDecision(ChairRole chair) {
        listAllPapers();
        String paperId = readLine("Paper ID to decide: ").trim();
        Paper paper = repo.findPaper(paperId);
        if (paper == null) {
            System.out.println("Paper not found.");
            return;
        }
        viewReviewsInline(paperId);
        String decision = readLine("Accept? (y/n): ").trim();
        boolean accept = decision.equalsIgnoreCase("y") || decision.equalsIgnoreCase("yes");
        if (chair.makeDecision(paper, accept, repo)) {
            System.out.println("Decision recorded: " + paper.getStatus());
        } else {
            System.out.println("Decision refused: paper is not UNDER_REVIEW (status: "
                    + paper.getStatus() + ").");
        }
    }

    private void viewReviewsInline(String paperId) {
        List<Review> reviews = reviewService.getReviewsForPaper(paperId);
        if (reviews.isEmpty()) {
            System.out.println("(No reviews received for this paper yet.)");
            return;
        }
        for (Review r : reviews) {
            System.out.println("  " + r);
        }
    }
}


