package com.darshangohil.urlshortener.domain.models;

/** What an audit entry records; each action is about either a link or a user. */
public enum AuditAction {
    LINK_EDITED("LINK", "Edited link"),
    LINK_DELETED("LINK", "Deleted link"),
    LINK_DISABLED("LINK", "Disabled link"),
    LINK_ENABLED("LINK", "Re-enabled link"),
    USER_REGISTERED("USER", "Registered"),
    USER_ROLE_CHANGED("USER", "Changed role"),
    USER_DISABLED("USER", "Disabled account"),
    USER_ENABLED("USER", "Re-enabled account");

    private final String targetType;
    private final String label;

    AuditAction(String targetType, String label) {
        this.targetType = targetType;
        this.label = label;
    }

    public String targetType() {
        return targetType;
    }

    public String label() {
        return label;
    }
}
