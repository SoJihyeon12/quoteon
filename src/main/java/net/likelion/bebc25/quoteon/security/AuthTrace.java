package net.likelion.bebc25.quoteon.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

final class AuthTrace {

    private static final Logger log = LoggerFactory.getLogger(AuthTrace.class);

    private AuthTrace() {
    }

    static void authorize(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info(
                "authorize sessionRef={} requestedRef={} contextRef={} thread={} method={} uri={} {}",
                sessionRef(request),
                requestedRef(request),
                contextRef(),
                Thread.currentThread().getName(),
                request.getMethod(),
                request.getRequestURI(),
                describe(authentication)
        );
    }

    static void statusBefore(HttpServletRequest request, Authentication authentication) {
        log.info(
                "status before sessionRef={} contextRef={} thread={} method={} uri={} {}",
                sessionRef(request),
                contextRef(),
                Thread.currentThread().getName(),
                request.getMethod(),
                request.getRequestURI(),
                describe(authentication)
        );
    }

    static void statusAfter(HttpServletRequest request, Authentication authentication) {
        log.info(
                "status after sessionRef={} contextRef={} thread={} method={} uri={} {}",
                sessionRef(request),
                contextRef(),
                Thread.currentThread().getName(),
                request.getMethod(),
                request.getRequestURI(),
                describe(authentication)
        );
    }

    static void denied(HttpServletRequest request, Authentication authentication, String exceptionName) {
        log.warn(
                "access denied sessionRef={} requestedRef={} contextRef={} thread={} method={} uri={} dispatcher={} exception={} {}",
                sessionRef(request),
                requestedRef(request),
                contextRef(),
                Thread.currentThread().getName(),
                request.getMethod(),
                request.getRequestURI(),
                request.getDispatcherType(),
                exceptionName,
                describe(authentication)
        );
    }

    static String sessionRef(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return "none";
        }
        return digest(session.getId());
    }

    static String requestedRef(HttpServletRequest request) {
        return digest(request.getRequestedSessionId());
    }

    static String contextRef() {
        return Integer.toHexString(System.identityHashCode(SecurityContextHolder.getContext()));
    }

    static String describe(Authentication authentication) {
        if (authentication == null) {
            return "principalId=none role=none authorities=[]";
        }
        if (authentication.getPrincipal() instanceof MemberPrincipal principal) {
            return "principalId=" + principal.id()
                    + " role=" + principal.role()
                    + " authorities=" + authentication.getAuthorities();
        }
        return "principalId=unknown role=unknown authorities=" + authentication.getAuthorities();
    }

    private static String digest(String value) {
        if (value == null || value.isBlank()) {
            return "none";
        }
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int index = 0; index < 4; index++) {
                hex.append(String.format("%02x", hash[index]));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            return "unavailable";
        }
    }
}
