package com.plantshop.user.exception;

/**
 * Thrown both for a subject that does not exist and for one the caller may not access, so the response
 * does not reveal whether somebody else's profile exists.
 */
public class SubjectNotFoundException extends RuntimeException {

    public SubjectNotFoundException() {
        super("Subject not found");
    }
}
