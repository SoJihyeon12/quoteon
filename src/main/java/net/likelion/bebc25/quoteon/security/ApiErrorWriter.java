package net.likelion.bebc25.quoteon.security;

import jakarta.servlet.http.HttpServletResponse;
import net.likelion.bebc25.quoteon.member.Member;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class ApiErrorWriter {

    void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        write(response, status, code, message, null, null, null, null);
    }

    void write(
            HttpServletResponse response,
            int status,
            String code,
            String message,
            String memberStatus,
            String name,
            String email,
            String companyName
    ) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{"
                + "\"status\":" + status
                + ",\"code\":\"" + escape(code) + "\""
                + ",\"message\":\"" + escape(message) + "\""
                + ",\"memberStatus\":" + jsonString(memberStatus)
                + ",\"name\":" + jsonString(name)
                + ",\"email\":" + jsonString(email)
                + ",\"companyName\":" + jsonString(companyName)
                + "}");
    }

    void writeMemberDenied(HttpServletResponse response, Member member) throws IOException {
        write(
                response,
                HttpServletResponse.SC_FORBIDDEN,
                member.status(),
                net.likelion.bebc25.quoteon.member.MemberStatus.message(member.status()),
                member.status(),
                member.name(),
                member.email(),
                member.companyName()
        );
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + escape(value) + "\"";
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}
