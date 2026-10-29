package net.likelion.bebc25.quoteon.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

final class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorWriter writer = new ApiErrorWriter();

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        writer.write(
                response,
                HttpStatus.UNAUTHORIZED.value(),
                "UNAUTHENTICATED",
                "로그인이 필요합니다."
        );
    }
}
