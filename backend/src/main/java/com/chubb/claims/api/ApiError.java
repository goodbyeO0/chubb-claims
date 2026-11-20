package com.chubb.claims.api;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public record ApiError(String code, String message) {
    public static ApiError of(String code) {
        return new ApiError(code, switch (code) {
            case "UNAUTHENTICATED" -> "Sign in to continue.";
            case "BAD_CREDENTIALS" -> "Sign-in failed.";
            case "CSRF_INVALID" -> "Refresh the CSRF token.";
            case "FORBIDDEN" -> "Access denied.";
            case "NOT_FOUND" -> "Claim or request not found.";
            case "VERSION_CONFLICT" -> "This claim was updated. Refresh to continue.";
            case "INVALID_STATE" -> "This action is unavailable in the current state.";
            default -> "Check the submitted fields.";
        });
    }
    public static void write(HttpServletResponse response, int status, String code) throws IOException {
        var error = of(code);
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"" + error.code + "\",\"message\":\"" + error.message + "\"}");
    }
}
