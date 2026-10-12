package com.conference.model;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A registered user of the conference system.
 * One account may hold several roles (Author / Reviewer / Chair) - 1..* composition.
 * Login requires the user ID plus password; passwords are stored as
 * SHA-256 hashes, never in plain text.
 */
public class UserAccount implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String userId;
    private String name;
    private String email;
    private String institution;
    private String passwordHash;
    private final Set<ConferenceRole> roles = new LinkedHashSet<ConferenceRole>();

    public UserAccount(String userId, String name, String email, String institution) {
        if (userId == null || userId.trim().isEmpty()) {
            throw new IllegalArgumentException("userId must not be empty");
        }
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.institution = institution;
    }

    // ---- password handling ----

    /** Sets the password; only its SHA-256 hash is kept. */
    public void setPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
        this.passwordHash = sha256(plainPassword);
    }

    /** Returns true if the given plain-text password matches the stored hash. */
    public boolean checkPassword(String plainPassword) {
        return passwordHash != null && passwordHash.equals(sha256(plainPassword));
    }

    /** True if this account already has a password (false for legacy data). */
    public boolean hasPassword() {
        return passwordHash != null;
    }

    private static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Adds a role to this account (idempotent thanks to Set semantics). */
    public void addRole(ConferenceRole role) {
        if (role == null) {
            throw new IllegalArgumentException("role must not be null");
        }
        roles.add(role);
    }

    /**
     * Checks whether this account holds a role by its type name,
     * e.g. "AuthorRole", "ReviewerRole", "ChairRole" (case-insensitive,
     * also accepts short names like "author").
     */
    public boolean hasRole(String roleType) {
        if (roleType == null) {
            return false;
        }
        String wanted = roleType.trim().toLowerCase();
        for (ConferenceRole role : roles) {
            String simple = role.getClass().getSimpleName().toLowerCase();
            if (simple.equals(wanted) || simple.equals(wanted + "role")) {
                return true;
            }
        }
        return false;
    }

    /** Returns the role instance of the given concrete type, or null if absent. */
    @SuppressWarnings("unchecked")
    public <T extends ConferenceRole> T getRole(Class<T> roleClass) {
        for (ConferenceRole role : roles) {
            if (roleClass.isInstance(role)) {
                return (T) role;
            }
        }
        return null;
    }

    public Set<ConferenceRole> getRoles() {
        return Collections.unmodifiableSet(roles);
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getInstitution() {
        return institution;
    }

    public void setInstitution(String institution) {
        this.institution = institution;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (ConferenceRole r : roles) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(r.getRoleName());
        }
        return String.format("[%s] %s <%s> @ %s | roles: %s",
                userId, name, email, institution, sb.length() == 0 ? "(none)" : sb);
    }
}
