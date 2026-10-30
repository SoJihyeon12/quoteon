package net.likelion.bebc25.quoteon.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.likelion.bebc25.quoteon.member.Member;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public class SessionAuthenticator {

    private static final Logger log = LoggerFactory.getLogger(SessionAuthenticator.class);

    private final SecurityContextRepository securityContextRepository;

    public SessionAuthenticator(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    public void start(HttpServletRequest request, HttpServletResponse response, Member member) {
        HttpSession existingSession = request.getSession(false);
        if (existingSession != null) {
            request.changeSessionId();
        }
        MemberPrincipal principal = MemberPrincipal.from(member);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        this.securityContextRepository.saveContext(context, request, response);
        Object storedAuthorities = null;
        HttpSession savedSession = request.getSession(false);
        if (savedSession != null
                && savedSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
                instanceof SecurityContext stored
                && stored.getAuthentication() != null) {
            storedAuthorities = stored.getAuthentication().getAuthorities();
        }
        log.info(
                "login session sessionRef={} contextRef={} memberId={} dbRole={} tokenAuthorities={} storedAuthorities={}",
                AuthTrace.sessionRef(request),
                AuthTrace.contextRef(),
                member.id(),
                member.role(),
                authentication.getAuthorities(),
                storedAuthorities
        );
    }
}
