package net.likelion.bebc25.quoteon.admin;

import net.likelion.bebc25.quoteon.admin.dto.MemberSummaryPageResponse;
import net.likelion.bebc25.quoteon.admin.dto.MemberSummaryResponse;
import net.likelion.bebc25.quoteon.admin.dto.PendingMemberPageResponse;
import net.likelion.bebc25.quoteon.admin.dto.PendingMemberResponse;
import net.likelion.bebc25.quoteon.admin.dto.UpdateMemberRequest;
import net.likelion.bebc25.quoteon.auth.dto.MemberResponse;
import net.likelion.bebc25.quoteon.member.EmailAlreadyExistsException;
import net.likelion.bebc25.quoteon.member.Member;
import net.likelion.bebc25.quoteon.member.MemberNotFoundException;
import net.likelion.bebc25.quoteon.member.MemberNotPendingException;
import net.likelion.bebc25.quoteon.member.MemberStatusConflictException;
import net.likelion.bebc25.quoteon.member.MemberRepository;
import net.likelion.bebc25.quoteon.member.MemberStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminMemberService {

    private static final int DEFAULT_PAGE = 0;
    private static final int MAX_SIZE = 100;

    private final MemberRepository memberRepository;

    public AdminMemberService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    public PendingMemberPageResponse getPendingMembers(String keyword, int page, int size) {
        if (page < DEFAULT_PAGE) {
            throw new InvalidMemberQueryException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidMemberQueryException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        long totalElements = memberRepository.countPending(normalizedKeyword);
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        List<PendingMemberResponse> items = memberRepository
                .findPending(normalizedKeyword, size, (long) page * size)
                .stream()
                .map(PendingMemberResponse::from)
                .toList();
        return new PendingMemberPageResponse(items, page, size, totalElements, totalPages);
    }

    public MemberSummaryPageResponse getMembers(String keyword, int page, int size) {
        if (page < DEFAULT_PAGE) {
            throw new InvalidMemberQueryException("페이지 번호는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidMemberQueryException("페이지 크기는 1 이상 100 이하여야 합니다.");
        }
        String normalizedKeyword = normalizeKeyword(keyword);
        long totalElements = memberRepository.countMembers(normalizedKeyword);
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        List<MemberSummaryResponse> items = memberRepository
                .findMembers(normalizedKeyword, size, (long) page * size)
                .stream()
                .map(MemberSummaryResponse::from)
                .toList();
        return new MemberSummaryPageResponse(items, page, size, totalElements, totalPages);
    }

    public MemberSummaryResponse getMember(long memberId) {
        return memberRepository.findSummaryById(memberId)
                .map(MemberSummaryResponse::from)
                .orElseThrow(MemberNotFoundException::new);
    }

    public MemberSummaryResponse updateMember(long memberId, UpdateMemberRequest request) {
        if (memberRepository.findSummaryById(memberId).isEmpty()) {
            throw new MemberNotFoundException();
        }
        String email = request.email().trim();
        memberRepository.findByEmail(email).ifPresent(existing -> {
            if (existing.id() != memberId) {
                throw new EmailAlreadyExistsException();
            }
        });
        int updated = memberRepository.updateProfile(
                memberId,
                request.name().trim(),
                email,
                blankToNull(request.phone()),
                blankToNull(request.companyName()),
                request.role()
        );
        if (updated != 1) {
            throw new MemberNotFoundException();
        }
        return memberRepository.findSummaryById(memberId)
                .map(MemberSummaryResponse::from)
                .orElseThrow(MemberNotFoundException::new);
    }

    public MemberSummaryResponse deactivate(long memberId) {
        return changeStatus(
                memberId,
                MemberStatus.INACTIVE,
                MemberStatus.ACTIVE,
                "활성 회원만 비활성화할 수 있습니다."
        );
    }

    public MemberSummaryResponse activate(long memberId) {
        return changeStatus(
                memberId,
                MemberStatus.ACTIVE,
                MemberStatus.INACTIVE,
                "비활성 회원만 활성화할 수 있습니다."
        );
    }

    public MemberResponse approve(long memberId) {
        return changePendingStatus(memberId, MemberStatus.ACTIVE);
    }

    public MemberResponse reject(long memberId) {
        return changePendingStatus(memberId, MemberStatus.REJECTED);
    }

    private MemberSummaryResponse changeStatus(
            long memberId,
            String newStatus,
            String expectedStatus,
            String conflictMessage
    ) {
        int updated = memberRepository.updateStatusIfCurrent(memberId, newStatus, expectedStatus);
        if (updated == 1) {
            return memberRepository.findSummaryById(memberId)
                    .map(MemberSummaryResponse::from)
                    .orElseThrow(MemberNotFoundException::new);
        }
        if (memberRepository.findSummaryById(memberId).isEmpty()) {
            throw new MemberNotFoundException();
        }
        throw new MemberStatusConflictException(conflictMessage);
    }

    private MemberResponse changePendingStatus(long memberId, String status) {
        int updated = memberRepository.updateStatusIfPending(memberId, status);
        if (updated == 1) {
            Member member = memberRepository.findById(memberId).orElseThrow(MemberNotFoundException::new);
            return MemberResponse.from(member);
        }
        if (memberRepository.findById(memberId).isEmpty()) {
            throw new MemberNotFoundException();
        }
        throw new MemberNotPendingException();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
