package com.conference.persistence;

import com.conference.model.Paper;
import com.conference.model.Review;
import com.conference.model.UserAccount;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serializable in-memory snapshot of the whole system state.
 * This is the single object written to / read from disk.
 */
class DataStore implements Serializable {

    private static final long serialVersionUID = 1L;

    final Map<String, UserAccount> users = new LinkedHashMap<String, UserAccount>();
    final Map<String, Paper> papers = new LinkedHashMap<String, Paper>();
    final Map<String, Review> reviews = new LinkedHashMap<String, Review>();

    int userSeq;
    int paperSeq;
    int reviewSeq;
}
