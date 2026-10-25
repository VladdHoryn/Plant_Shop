package com.plantshop.user.exception;

public class EmailAlreadyRegisteredException extends RuntimeException {

    public EmailAlreadyRegisteredException() {
        super("This e-mail address is already registered");
    }
}
