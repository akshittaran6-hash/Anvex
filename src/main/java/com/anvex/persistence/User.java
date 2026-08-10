package com.anvex.persistence;

public final class User {

    private final long id;
    private final String username;
    private final String passwordHash;
    private final String role;
    private final boolean enabled;

    public User(long id, String username, String passwordHash, String role, boolean enabled) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isEnabled() { return enabled; }

    public boolean isTarget() { return "TARGET".equals(role); }
    public boolean isLegitimate() { return "LEGITIMATE".equals(role); }

    @Override
    public String toString() {
        return String.format("User{id=%d, username='%s', role='%s', enabled=%s}", id, username, role, enabled);
    }
}