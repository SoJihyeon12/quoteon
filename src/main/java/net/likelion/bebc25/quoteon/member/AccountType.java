package net.likelion.bebc25.quoteon.member;

public enum AccountType {
    SALES(MemberRole.USER),
    ADMIN(MemberRole.ADMIN);

    private final String role;

    AccountType(String role) {
        this.role = role;
    }

    public String role() {
        return role;
    }
}
