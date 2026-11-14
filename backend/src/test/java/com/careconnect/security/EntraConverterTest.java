package com.careconnect.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class EntraConverterTest {

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("subject-id")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
    }

    @Test
    void usesUsernameAndMapsAppRoles() {
        var auth = new SecurityConfig.EntraConverter().convert(token()
                .claim("preferred_username", "dr@example.test")
                .claim("roles", java.util.List.of("CLINICIAN")).build());
        assertThat(auth.getName()).isEqualTo("dr@example.test");
        assertThat(auth.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_CLINICIAN");
    }

    @Test
    void aTokenWithoutRolesGetsNoAuthorities() {
        var auth = new SecurityConfig.EntraConverter().convert(token().build());
        assertThat(auth.getName()).isEqualTo("subject-id");
        assertThat(auth.getAuthorities()).isEmpty();
    }
}
