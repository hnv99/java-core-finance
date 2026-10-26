package com.fincore.exception;

/**
 * Unchecked wrapper for storage failures (I/O errors, corrupted files).
 * <p>
 * Business code cannot recover from a broken disk, so forcing every caller to declare
 * {@code IOException} would only add noise. The low-level cause is preserved.
 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }

    public DataAccessException(String message) {
        super(message);
    }
}
