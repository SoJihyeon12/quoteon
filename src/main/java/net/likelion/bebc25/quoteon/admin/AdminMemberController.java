package net.likelion.bebc25.quoteon.admin;

import jakarta.validation.Valid;
import net.likelion.bebc25.quoteon.admin.dto.MemberSummaryPageResponse;
import net.likelion.bebc25.quoteon.admin.dto.MemberSummaryResponse;
import net.likelion.bebc25.quoteon.admin.dto.PendingMemberPageResponse;
import net.likelion.bebc25.quoteon.admin.dto.UpdateMemberRequest;
import net.likelion.bebc25.quoteon.auth.dto.MemberResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @GetMapping
    public MemberSummaryPageResponse members(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminMemberService.getMembers(keyword, page, size);
    }

    @GetMapping("/pending")
    public PendingMemberPageResponse pending(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminMemberService.getPendingMembers(keyword, page, size);
    }

    @GetMapping("/{memberId}")
    public MemberSummaryResponse member(@PathVariable long memberId) {
        return adminMemberService.getMember(memberId);
    }

    @PutMapping("/{memberId}")
    public MemberSummaryResponse update(
            @PathVariable long memberId,
            @Valid @RequestBody UpdateMemberRequest request
    ) {
        return adminMemberService.updateMember(memberId, request);
    }

    @PostMapping("/{memberId}/deactivate")
    public MemberSummaryResponse deactivate(@PathVariable long memberId) {
        return adminMemberService.deactivate(memberId);
    }

    @PostMapping("/{memberId}/activate")
    public MemberSummaryResponse activate(@PathVariable long memberId) {
        return adminMemberService.activate(memberId);
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
