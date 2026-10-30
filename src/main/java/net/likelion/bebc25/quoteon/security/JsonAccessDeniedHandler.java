package net.likelion.bebc25.quoteon.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import java.io.IOException;

final class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorWriter writer = new ApiErrorWriter();

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuthTrace.denied(request, authentication, accessDeniedException.getClass().getName());
        if (accessDeniedException instanceof CsrfException) {
            writer.write(response, HttpStatus.FORBIDDEN.value(), "CSRF", "CSRF 토큰이 유효하지 않습니다.");
            return;
        }
        writer.write(response, HttpStatus.FORBIDDEN.value(), "FORBIDDEN", "이 기능을 사용할 권한이 없습니다.");
    }
}
