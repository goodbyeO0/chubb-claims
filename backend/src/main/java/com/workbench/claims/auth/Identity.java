package com.workbench.claims.auth;

import java.util.UUID;

public record Identity(UUID id, String displayName, String role) {}
