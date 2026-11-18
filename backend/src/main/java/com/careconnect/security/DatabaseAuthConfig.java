package com.careconnect.security;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Sign-in with CareConnect's own accounts (careconnect.auth.mode=database). Terraform allows this outside
 * production only; production signs in with Entra ID and MFA.
 */
@Configuration
@ConditionalOnProperty(name = "careconnect.auth.mode", havingValue = "database")
public class DatabaseAuthConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseAuthConfig.class);

    @Bean
    TokenService tokenService(@Value("${careconnect.auth.jwt-secret:}") String secret,
                              @Value("${careconnect.auth.token-minutes:60}") long minutes) {
        return new TokenService(secret, minutes);
    }

    @Bean
    JwtDecoder careConnectJwtDecoder(TokenService tokens) {
        return tokens.decoder();
    }

    /**
     * Roles are read from the database on every request, so a role change or a disabled account takes effect at
     * once instead of when the token expires. An account that must change its password holds no roles.
     */
    @Bean
    DatabaseConverter databaseConverter(AccountService accounts) {
        return new DatabaseConverter(accounts);
    }

    /** Creates the first administrator from CARECONNECT_BOOTSTRAP_ADMIN_PASSWORD if no administrator exists. */
    @Bean
    Bootstrap bootstrap(AccountService accounts, @Value("${careconnect.auth.bootstrap-admin-password:}") String password) {
        return new Bootstrap(accounts, password);
    }

    public static class DatabaseConverter implements Converter<Jwt, AbstractAuthenticationToken> {
        private final AccountService accounts;

        DatabaseConverter(AccountService accounts) { this.accounts = accounts; }

        @Override
        public AbstractAuthenticationToken convert(Jwt jwt) {
            String username = jwt.getSubject();
            var account = accounts.byUsername(username).filter(AccountService.Account::enabled).orElse(null);
            List<SimpleGrantedAuthority> authorities = account == null || account.mustChangePassword() ? List.of()
                    : account.roles().stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
            return new JwtAuthenticationToken(jwt, authorities, username);
        }
    }

    public static class Bootstrap {
        private final AccountService accounts;
        private final String password;

        Bootstrap(AccountService accounts, String password) {
            this.accounts = accounts;
            this.password = password;
        }

        @EventListener(ApplicationReadyEvent.class)
        public void createFirstAdmin() {
            if (password == null || password.isBlank() || accounts.hasEnabledAdmin()) {
                return;
            }
            try {
                accounts.create("admin", "Administrator", List.of(Roles.ADMIN), password, "bootstrap");
                log.info("Created the first administrator account 'admin'. Its password must be changed at first sign-in.");
            } catch (RuntimeException e) {
                // Another replica created it first, or the password does not meet the policy.
                log.warn("First administrator not created: {}", e.getMessage());
            }
        }
    }
}
