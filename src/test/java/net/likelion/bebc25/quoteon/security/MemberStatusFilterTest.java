package net.likelion.bebc25.quoteon.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberStatusFilterTest {

    @Test
    void pendingMemberCanOnlyReadProfileAndLogout() {
        assertTrue(MemberStatusFilter.isPendingAllowed("GET", "/api/me"));
        assertTrue(MemberStatusFilter.isPendingAllowed("POST", "/api/logout"));
        assertFalse(MemberStatusFilter.isPendingAllowed("GET", "/api/categories"));
        assertFalse(MemberStatusFilter.isPendingAllowed("GET", "/api/products"));
        assertFalse(MemberStatusFilter.isPendingAllowed("GET", "/api/admin/members/pending"));
        assertFalse(MemberStatusFilter.isPendingAllowed("POST", "/api/admin/members/1/approve"));
        assertFalse(MemberStatusFilter.isPendingAllowed("POST", "/api/admin/members/1/reject"));
        assertFalse(MemberStatusFilter.isPendingAllowed("POST", "/api/login"));
        assertFalse(MemberStatusFilter.isPendingAllowed("POST", "/api/signup"));
    }
}
