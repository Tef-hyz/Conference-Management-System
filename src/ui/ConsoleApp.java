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

}
