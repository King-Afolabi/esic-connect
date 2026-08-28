package com.esic.connect.identity.internal;

public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds) {
}
