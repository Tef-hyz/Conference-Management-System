package com.conference.model;

import java.io.Serializable;

/**
 * A review written by a ReviewerRole for one paper.
 */
public class Review implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final int MIN_SCORE = 1;
    public static final int MAX_SCORE = 5;

    private final String reviewId;
    private final String paperId;
    private final String reviewerId;
    private final int score;
    private final int confidence;
    private final String comments;

    public Review(String reviewId, String paperId, String reviewerId,
                  int score, int confidence, String comments) {
        if (score < MIN_SCORE || score > MAX_SCORE) {
            throw new IllegalArgumentException("score must be between " + MIN_SCORE + " and " + MAX_SCORE);
        }
        if (confidence < MIN_SCORE || confidence > MAX_SCORE) {
            throw new IllegalArgumentException("confidence must be between " + MIN_SCORE + " and " + MAX_SCORE);
        }
        if (reviewId == null || paperId == null || reviewerId == null || comments == null || comments.trim().isEmpty())
            throw new IllegalArgumentException("Review IDs and non-empty comments are required.");
        this.reviewId = reviewId;
        this.paperId = paperId;
        this.reviewerId = reviewerId;
        this.score = score;
        this.confidence = confidence;
        this.comments = comments.trim();
    }

    public String getReviewId() {
        return reviewId;
    }

    public String getPaperId() {
        return paperId;
    }

    public String getReviewerId() {
        return reviewerId;
    }

    public int getScore() {
        return score;
    }

    public int getConfidence() {
        return confidence;
    }

    public String getComments() {
        return comments;
    }

    @Override
    public String toString() {
        return String.format("[%s] paper=%s reviewer=%s score=%d confidence=%d | %s",
                reviewId, paperId, reviewerId, score, confidence, comments);
    }
}
