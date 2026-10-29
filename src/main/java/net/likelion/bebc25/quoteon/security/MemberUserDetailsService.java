package net.likelion.bebc25.quoteon.security;

import net.likelion.bebc25.quoteon.member.MemberRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MemberUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    public MemberUserDetailsService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return memberRepository.findByEmail(username)
                .map(MemberPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("member not found"));
    }
}
