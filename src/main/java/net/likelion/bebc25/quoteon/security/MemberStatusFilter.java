package net.likelion.bebc25.quoteon.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.likelion.bebc25.quoteon.member.Member;
import net.likelion.bebc25.quoteon.member.MemberRepository;
import net.likelion.bebc25.quoteon.member.MemberStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class MemberStatusFilter extends OncePerRequestFilter {

    private final MemberRepository memberRepository;
    private final ApiErrorWriter writer = new ApiErrorWriter();

    public MemberStatusFilter(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    static boolean isPendingAllowed(String method, String path) {
        if (HttpMethod.GET.matches(method) && "/api/me".equals(path)) {
            return true;
        }
        return HttpMethod.POST.matches(method) && "/api/logout".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!(authentication.getPrincipal() instanceof MemberPrincipal principal)) {
            writer.write(
                    response,
                    HttpStatus.FORBIDDEN.value(),
                    "FORBIDDEN",
                    "이 기능을 사용할 권한이 없습니다."
            );
            return;
        }

        Member member = memberRepository.findById(principal.id()).orElse(null);
        if (member == null) {
            clearSession(request);
            writer.write(response, HttpStatus.UNAUTHORIZED.value(), "UNAUTHENTICATED", "로그인이 필요합니다.");
            return;
        }
        if (MemberStatus.isRejectedOrInactive(member.status()) || !isKnownStatus(member.status())) {
            clearSession(request);
            writer.writeMemberDenied(response, member);
            return;
        }
        if (MemberStatus.PENDING.equals(member.status())
                && !isPendingAllowed(request.getMethod(), request.getRequestURI())) {
            writer.writeMemberDenied(response, member);
            return;
        }

        MemberPrincipal refreshed = MemberPrincipal.from(member);
        var refreshedAuthentication = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                refreshed,
                null,
                refreshed.getAuthorities()
        );
        SecurityContextHolder.getContext().setAuthentication(refreshedAuthentication);
        filterChain.doFilter(request, response);
    }

    private static boolean isKnownStatus(String status) {
        return MemberStatus.PENDING.equals(status) || MemberStatus.ACTIVE.equals(status)
                || MemberStatus.REJECTED.equals(status) || MemberStatus.INACTIVE.equals(status);
    }

    private static void clearSession(HttpServletRequest request) {
        SecurityContextHolder.clearContext();
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
