package com.juanbarros.helpdesk.user;

public enum Role {
    /** Usuario final: crea tickets y sigue los suyos. */
    USER,
    /** Técnico de soporte: atiende, asigna y resuelve tickets. */
    AGENT,
    /** Administrador: todo lo del agente más la gestión de usuarios. */
    ADMIN;

    public boolean isStaff() {
        return this == AGENT || this == ADMIN;
    }
}
