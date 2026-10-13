package com.conference.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable anonymous projection of a Paper, exposed to reviewers.
 * Contains no author identity or institution information.
 */
public class AnonymousPaperView {

    private final String paperId;
    private final String title;
    private final String abstractText;
    private final List<String> topics;

    public AnonymousPaperView(String paperId, String title, String abstractText, List<String> topics) {
        this.paperId = paperId;
        this.title = title;
        this.abstractText = abstractText;
        this.topics = new ArrayList<String>(topics);
    }

    public String getPaperId() {
        return paperId;
    }

    public String getTitle() {
        return title;
    }

    public String getAbstractText() {
        return abstractText;
    }

    public List<String> getTopics() {
        return Collections.unmodifiableList(topics);
    }

    @Override
    public String toString() {
        return String.format("[%s] \"%s\"\n  Topics: %s\n  Abstract: %s",
                paperId, title, topics, abstractText);
    }
}
