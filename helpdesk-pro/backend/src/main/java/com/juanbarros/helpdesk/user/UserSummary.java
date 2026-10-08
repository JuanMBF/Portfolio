package com.juanbarros.helpdesk.user;

/** Versión reducida de un usuario para incrustar en tickets y comentarios. */
public record UserSummary(Long id, String fullName, Role role) {

    public static UserSummary from(User user) {
        return user == null ? null : new UserSummary(user.getId(), user.getFullName(), user.getRole());
    }
}
