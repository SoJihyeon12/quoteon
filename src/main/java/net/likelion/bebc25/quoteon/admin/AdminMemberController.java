package net.likelion.bebc25.quoteon.admin;

import net.likelion.bebc25.quoteon.admin.dto.PendingMemberPageResponse;
import net.likelion.bebc25.quoteon.auth.dto.MemberResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final AdminMemberService adminMemberService;

    public AdminMemberController(AdminMemberService adminMemberService) {
        this.adminMemberService = adminMemberService;
    }

    @GetMapping("/pending")
    public PendingMemberPageResponse pending(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminMemberService.getPendingMembers(keyword, page, size);
    }

    @PostMapping("/{memberId}/approve")
    public MemberResponse approve(@PathVariable long memberId) {
        return adminMemberService.approve(memberId);
    }

    @PostMapping("/{memberId}/reject")
    public MemberResponse reject(@PathVariable long memberId) {
        return adminMemberService.reject(memberId);
    }
}
