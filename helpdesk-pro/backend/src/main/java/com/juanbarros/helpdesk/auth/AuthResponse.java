package com.juanbarros.helpdesk.auth;

import com.juanbarros.helpdesk.user.UserResponse;

public record AuthResponse(String token, String tokenType, long expiresIn, UserResponse user) {
}
