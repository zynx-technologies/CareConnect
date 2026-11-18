package com.careconnect.security;

import com.careconnect.common.ApiErrors;
import com.careconnect.common.Db;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * CareConnect's own accounts, for environments that do not use Entra ID. Passwords are stored only as bcrypt
 * hashes. Five wrong passwords lock an account for 15 minutes. Temporary passwords must be changed at first
 * sign-in, and until then the account holds no roles.
 */
@Service
public class AccountService {

    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration LOCKOUT = Duration.ofMinutes(15);
    static final int MIN_PASSWORD_LENGTH = 12;

    public record Account(String id, String username, String displayName, List<String> roles, boolean enabled,
                          boolean mustChangePassword, Instant lastLoginAt, Instant lockedUntil) {}

    private record Row(String id, String username, String displayName, String passwordHash, String roles, int enabled,
                       int mustChangePassword, int failedAttempts, Instant lockedUntil, Instant lastLoginAt) {
        Account account() {
            return new Account(id, username, displayName, parseRoles(roles), enabled == 1, mustChangePassword == 1,
                    lastLoginAt, lockedUntil);
        }
    }

    private static final String COLUMNS = """
            SELECT id, username, display_name, password_hash, roles, enabled, must_change_password, failed_attempts,
                   locked_until, last_login_at FROM app_user""";

    private final JdbcClient jdbc;
    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    /** Compared against when the username does not exist, so a wrong username takes as long as a wrong password. */
    private final String dummyHash = encoder.encode("not-a-real-password-" + Db.newId());

    public AccountService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Returns the account if the password is right. Every failure gives the same message. */
    public Account authenticate(String username, String password) {
        Optional<Row> found = find(normalise(username));
        if (found.isEmpty()) {
            encoder.matches(password, dummyHash);
            throw new SignInFailed();
        }
        Row row = found.get();
        if (row.lockedUntil() != null && row.lockedUntil().isAfter(Instant.now())) {
            throw new SignInFailed();
        }
        if (!encoder.matches(password, row.passwordHash()) || row.enabled() != 1) {
            int attempts = row.failedAttempts() + 1;
            Timestamp lock = attempts >= MAX_FAILED_ATTEMPTS ? Timestamp.from(Instant.now().plus(LOCKOUT)) : null;
            jdbc.sql("UPDATE app_user SET failed_attempts = ?, locked_until = ? WHERE id = ?")
                    .params(lock == null ? attempts : 0, lock, row.id()).update();
            throw new SignInFailed();
        }
        jdbc.sql("UPDATE app_user SET failed_attempts = 0, locked_until = NULL, last_login_at = ? WHERE id = ?")
                .params(Db.now(), row.id()).update();
        return row.account();
    }

    public Optional<Account> byUsername(String username) {
        return find(normalise(username)).map(Row::account);
    }

    public Optional<Account> byId(String id) {
        return jdbc.sql(COLUMNS + " WHERE id = ?").param(id).query(Row.class).optional().map(Row::account);
    }

    public List<Account> all() {
        return jdbc.sql(COLUMNS + " ORDER BY username").query(Row.class).list().stream().map(Row::account).toList();
    }

    public boolean hasEnabledAdmin() {
        return all().stream().anyMatch(a -> a.enabled() && a.roles().contains(Roles.ADMIN));
    }

    public Account create(String username, String displayName, List<String> roles, String temporaryPassword, String createdBy) {
        String name = normalise(username);
        if (!name.matches("[a-z0-9][a-z0-9._-]{1,49}")) {
            throw new ApiErrors.BadRequest("Use 2 to 50 lower-case letters, digits, dots, dashes, or underscores for the username");
        }
        checkPassword(name, temporaryPassword);
        if (find(name).isPresent()) {
            throw new ApiErrors.Conflict("That username is already taken");
        }
        String id = Db.newId();
        jdbc.sql("""
                INSERT INTO app_user (id, username, display_name, password_hash, roles, enabled, must_change_password,
                                      failed_attempts, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, 1, 1, 0, ?, ?)""")
                .params(id, name, displayName.strip(), encoder.encode(temporaryPassword), joinRoles(roles), Db.now(), createdBy)
                .update();
        return byId(id).orElseThrow();
    }

    public void changePassword(String username, String currentPassword, String newPassword) {
        Account account;
        try {
            account = authenticate(username, currentPassword);
        } catch (SignInFailed e) {
            throw new ApiErrors.BadRequest("The current password is incorrect");
        }
        if (currentPassword.equals(newPassword)) {
            throw new ApiErrors.BadRequest("Choose a password you have not just used");
        }
        checkPassword(account.username(), newPassword);
        jdbc.sql("UPDATE app_user SET password_hash = ?, must_change_password = 0, password_changed_at = ? WHERE id = ?")
                .params(encoder.encode(newPassword), Db.now(), account.id()).update();
    }

    /** Sets a temporary password, which the person must change at their next sign-in. Also unlocks the account. */
    public void resetPassword(String id, String temporaryPassword) {
        Account account = byId(id).orElseThrow(() -> new ApiErrors.NotFound("User not found"));
        checkPassword(account.username(), temporaryPassword);
        jdbc.sql("""
                UPDATE app_user SET password_hash = ?, must_change_password = 1, failed_attempts = 0, locked_until = NULL
                WHERE id = ?""")
                .params(encoder.encode(temporaryPassword), id).update();
    }

    public void update(String id, String displayName, List<String> roles, boolean enabled) {
        int updated = jdbc.sql("UPDATE app_user SET display_name = ?, roles = ?, enabled = ? WHERE id = ?")
                .params(displayName.strip(), joinRoles(roles), enabled ? 1 : 0, id).update();
        if (updated == 0) {
            throw new ApiErrors.NotFound("User not found");
        }
    }

    static void checkPassword(String username, String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > 128) {
            throw new ApiErrors.BadRequest("Passwords must be 12 to 128 characters");
        }
        if (password.toLowerCase().contains(username.toLowerCase())) {
            throw new ApiErrors.BadRequest("Passwords must not contain the username");
        }
        int kinds = (password.matches(".*[a-z].*") ? 1 : 0) + (password.matches(".*[A-Z].*") ? 1 : 0)
                + (password.matches(".*[0-9].*") ? 1 : 0) + (password.matches(".*[^A-Za-z0-9].*") ? 1 : 0);
        if (kinds < 3) {
            throw new ApiErrors.BadRequest("Use at least three of: lower-case, upper-case, digits, and symbols");
        }
    }

    private Optional<Row> find(String username) {
        return jdbc.sql(COLUMNS + " WHERE username = ?").param(username).query(Row.class).optional();
    }

    private static String normalise(String username) {
        return username == null ? "" : username.strip().toLowerCase();
    }

    private static String joinRoles(List<String> roles) {
        Set<String> clean = new LinkedHashSet<>();
        for (String role : roles) {
            String r = role.strip().toUpperCase();
            if (!Roles.ALL.contains(r)) {
                throw new ApiErrors.BadRequest("Unknown role: " + r);
            }
            clean.add(r);
        }
        return String.join(",", clean);
    }

    static List<String> parseRoles(String roles) {
        return roles == null || roles.isBlank() ? List.of()
                : Arrays.stream(roles.split(",")).map(String::strip).filter(r -> !r.isEmpty()).toList();
    }

    /** Wrong username, wrong password, locked, or disabled: the caller is never told which. */
    public static class SignInFailed extends RuntimeException {
        public SignInFailed() { super("The username or password is incorrect, or the account is locked"); }
    }
}
