package com.careconnect.security;

import com.careconnect.common.Db;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Signs and checks CareConnect's own access tokens (HS256). The secret comes from the environment, never from code. */
public class TokenService {

    public static final String ISSUER = "careconnect";

    public record Issued(String token, Instant expiresAt) {}

    private final SecretKey key;
    private final NimbusJwtEncoder encoder;
    private final Duration lifetime;

    public TokenService(@Value("${careconnect.auth.jwt-secret:}") String secret,
                        @Value("${careconnect.auth.token-minutes:60}") long minutes) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("CARECONNECT_JWT_SECRET must be set to at least 32 characters for database sign-in");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.lifetime = Duration.ofMinutes(minutes);
    }

    public Issued issue(AccountService.Account account) {
        Instant now = Instant.now();
        Instant expires = now.plus(lifetime);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER).subject(account.username()).id(Db.newId())
                .issuedAt(now).expiresAt(expires)
                .claim("preferred_username", account.username())
                .claim("name", account.displayName())
                .claim("roles", account.mustChangePassword() ? List.of() : account.roles())
                .claim("pwd_change", account.mustChangePassword())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new Issued(token, expires);
    }

    public JwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
        return decoder;
    }
}
