package com.conference.model;

import java.io.Serializable;

/** Immutable settings. Both assignment routes have an absolute limit of two. */
public final class ConferenceConfiguration implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final int MAX_REVIEWERS_PER_PAPER = 2;
    private final int requiredReviewers;
    private final int maxWorkload;
    private final double acceptanceThreshold;
    private final int disagreementThreshold;
    private final ConferencePhase phase;

    public ConferenceConfiguration() { this(2, 3, 3.0, 2, ConferencePhase.SUBMISSION); }

    public ConferenceConfiguration(int requiredReviewers, int maxWorkload,
            double acceptanceThreshold, int disagreementThreshold, ConferencePhase phase) {
        if (requiredReviewers < 1 || requiredReviewers > MAX_REVIEWERS_PER_PAPER)
            throw new IllegalArgumentException("Required reviewers must be between 1 and 2.");
        if (maxWorkload < 1 || maxWorkload > 100)
            throw new IllegalArgumentException("Maximum workload must be between 1 and 100.");
        if (Double.isNaN(acceptanceThreshold) || acceptanceThreshold < 1 || acceptanceThreshold > 5)
            throw new IllegalArgumentException("Acceptance threshold must be between 1 and 5.");
        if (disagreementThreshold < 1 || disagreementThreshold > 4 || phase == null)
            throw new IllegalArgumentException("Disagreement threshold must be between 1 and 4; phase is required.");
        this.requiredReviewers = requiredReviewers;
        this.maxWorkload = maxWorkload;
        this.acceptanceThreshold = acceptanceThreshold;
        this.disagreementThreshold = disagreementThreshold;
        this.phase = phase;
    }

    public int getRequiredReviewers() { return requiredReviewers; }
    public int getMaxWorkload() { return maxWorkload; }
    public double getAcceptanceThreshold() { return acceptanceThreshold; }
    public int getDisagreementThreshold() { return disagreementThreshold; }
    public ConferencePhase getPhase() { return phase; }
    public ConferenceConfiguration withPhase(ConferencePhase next) {
        return new ConferenceConfiguration(requiredReviewers, maxWorkload, acceptanceThreshold, disagreementThreshold, next);
    }
    @Override public String toString() {
        return "phase=" + phase + " | required reviewers=" + requiredReviewers + " | max workload=" + maxWorkload
                + " | acceptance threshold=" + acceptanceThreshold + " | disagreement threshold=" + disagreementThreshold;
    }
}
