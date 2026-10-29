package net.likelion.bebc25.quoteon.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.likelion.bebc25.quoteon.auth.dto.LoginRequest;
import net.likelion.bebc25.quoteon.auth.dto.MemberResponse;
import net.likelion.bebc25.quoteon.auth.dto.SignupRequest;
import net.likelion.bebc25.quoteon.member.EmailAlreadyExistsException;
import net.likelion.bebc25.quoteon.member.Member;
import net.likelion.bebc25.quoteon.member.MemberRepository;
import net.likelion.bebc25.quoteon.member.MemberStatus;
import net.likelion.bebc25.quoteon.security.SessionAuthenticator;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuthService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final SessionAuthenticator sessionAuthenticator;
    private final String dummyPasswordHash;

    public AuthService(
            MemberRepository memberRepository,
            PasswordEncoder passwordEncoder,
            SessionAuthenticator sessionAuthenticator
    ) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.sessionAuthenticator = sessionAuthenticator;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public MemberResponse signup(SignupRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = request.email().trim();
        if (memberRepository.findByEmail(email).isPresent()) {
            throw new EmailAlreadyExistsException();
        }
        long id = memberRepository.insertPending(
                email,
                passwordEncoder.encode(request.password()),
                request.name().trim(),
                blankToNull(request.phone()),
                blankToNull(request.companyName()),
                request.accountType().role()
        );
        Member member = memberRepository.findById(id).orElseThrow();
        sessionAuthenticator.start(httpRequest, httpResponse, member);
        return MemberResponse.from(member);
    }

    public MemberResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String email = request.email().trim();
        Member member = memberRepository.findByEmail(email).orElse(null);
        if (member == null) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(request.password(), member.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        if (!MemberStatus.ACTIVE.equals(member.status()) && !MemberStatus.PENDING.equals(member.status())) {
            throw new MemberAccessDeniedException(member);
        }
        sessionAuthenticator.start(httpRequest, httpResponse, member);
        return MemberResponse.from(member);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
