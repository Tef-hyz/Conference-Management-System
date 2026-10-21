package com.conference;

import com.conference.persistence.ConferenceRepository;
import com.conference.persistence.FileDataStore;
import com.conference.ui.ConsoleApp;

/**
 * Application entry point.
 * Wires the file-based repository into the CUI and starts the main loop.
 * No manual configuration is required: the data file is created automatically.
 */
public class Main {

    public static void main(String[] args) {
        try {
            ConferenceRepository repo = new FileDataStore();
            new ConsoleApp(repo).run();
        } catch (com.conference.persistence.StorageException e) {
            System.err.println("Storage error: " + e.getMessage());
        }
    }
}
