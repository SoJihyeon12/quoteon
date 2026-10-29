package net.likelion.bebc25.quoteon.security;

import net.likelion.bebc25.quoteon.member.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record MemberPrincipal(
        long id,
        String email,
        String name,
        String phone,
        String companyName,
        String role,
        String status
) implements UserDetails {

    public static MemberPrincipal from(Member member) {
        return new MemberPrincipal(
                member.id(),
                member.email(),
                member.name(),
                member.phone(),
                member.companyName(),
                member.role(),
                member.status()
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    public String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return email;
    }
}
