package com.juanbarros.helpdesk.common;

/** Se lanza cuando una operación es válida sintácticamente pero viola una regla de negocio. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
