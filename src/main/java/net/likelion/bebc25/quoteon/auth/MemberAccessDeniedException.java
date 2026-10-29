package net.likelion.bebc25.quoteon.auth;

import net.likelion.bebc25.quoteon.common.ApiErrorResponse;
import net.likelion.bebc25.quoteon.member.Member;
import net.likelion.bebc25.quoteon.member.MemberStatus;
import org.springframework.http.HttpStatus;

public class MemberAccessDeniedException extends RuntimeException {

    private final ApiErrorResponse response;

    public MemberAccessDeniedException(Member member) {
        super(MemberStatus.message(member.status()));
        this.response = new ApiErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                member.status(),
                MemberStatus.message(member.status()),
                member.status(),
                member.name(),
                member.email(),
                member.companyName()
        );
    }

    public ApiErrorResponse response() {
        return response;
    }
}
