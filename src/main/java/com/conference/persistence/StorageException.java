package com.conference.persistence;

/** Fatal storage failure: callers must not report a successful business operation. */
public class StorageException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public StorageException(String message, Throwable cause) { super(message, cause); }
}
