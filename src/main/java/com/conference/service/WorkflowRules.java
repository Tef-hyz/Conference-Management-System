package com.conference.service;

import com.conference.model.*;
import com.conference.persistence.ConferenceRepository;

/** Shared guards; menu visibility is not the only business-rule enforcement. */
public final class WorkflowRules {
    private WorkflowRules() { }
    public static void requireRole(ConferenceRole role, ConferenceRepository repo) {
        if (role == null || repo.findUser(role.getAccount().getUserId()) != role.getAccount()
                || role.getAccount().getRole(role.getClass()) != role)
            throw new IllegalStateException("An authorised, registered role is required.");
    }
    public static void requirePhase(ConferenceRepository repo, ConferencePhase phase) {
        if (repo.getConfiguration().getPhase() != phase)
            throw new IllegalStateException("This operation requires the " + phase + " phase.");
    }
}
