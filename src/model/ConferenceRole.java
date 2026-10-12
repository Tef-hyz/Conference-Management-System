package com.conference.model;

import java.io.Serializable;

/**
 * Abstract base of all conference roles.
 * Achieves abstraction: concrete behaviour lives in AuthorRole / ReviewerRole / ChairRole,
 * while the common link back to the owning account is defined once here.
 */
public abstract class ConferenceRole implements Serializable {

    private static final long serialVersionUID = 1L;

    protected final UserAccount account;

    protected ConferenceRole(UserAccount account) {
        if (account == null) {
            throw new IllegalArgumentException("A role must belong to a UserAccount");
        }
        this.account = account;
    }

    public UserAccount getAccount() {
        return account;
    }

    /** Human-readable role name, supplied by each concrete subclass. */
    public abstract String getRoleName();
}
