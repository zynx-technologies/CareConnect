package com.careconnect.security;

import com.careconnect.audit.AuditService;
import com.careconnect.common.ApiErrors;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Sign-in, password change, and account administration for CareConnect's own accounts. */
@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "careconnect.auth.mode", havingValue = "database")
public class AuthController {

    public record Login(@NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 128) String password) {}
    public record Session(String token, Instant expiresAt, String username, String displayName, List<String> roles,
                          boolean mustChangePassword) {}
    public record PasswordChange(@NotBlank String currentPassword, @NotBlank String newPassword) {}
    public record NewUser(@NotBlank String username, @NotBlank @Size(max = 200) String displayName,
                          @NotEmpty List<String> roles, @NotBlank String temporaryPassword) {}
    public record UserUpdate(@NotBlank @Size(max = 200) String displayName, @NotEmpty List<String> roles, boolean enabled) {}
    public record PasswordReset(@NotBlank String temporaryPassword) {}

    private final AccountService accounts;
    private final TokenService tokens;
    private final AuditService audit;
    private final CurrentUser user;

    public AuthController(AccountService accounts, TokenService tokens, AuditService audit, CurrentUser user) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.audit = audit;
        this.user = user;
    }

    @PostMapping("/auth/login")
    public Session login(@Valid @RequestBody Login login) {
        String username = login.username().strip().toLowerCase();
        try {
            AccountService.Account account = accounts.authenticate(username, login.password());
            audit.record(account.username(), "SIGN_IN", null, null, "ALLOWED", account.mustChangePassword() ? "password change required" : null);
            return session(account);
        } catch (AccountService.SignInFailed e) {
            audit.record(username, "SIGN_IN", null, null, "DENIED", null);
            throw e;
        }
    }

    @GetMapping("/auth/me")
    public Session me() {
        AccountService.Account account = accounts.byUsername(user.id()).orElseThrow(() -> new ApiErrors.NotFound("Account not found"));
        return new Session(null, null, account.username(), account.displayName(),
                account.mustChangePassword() ? List.of() : account.roles(), account.mustChangePassword());
    }

    /** Needs the current password even with a valid token, and returns a fresh token with the account's roles. */
    @PostMapping("/auth/password")
    public Session changePassword(@Valid @RequestBody PasswordChange change) {
        accounts.changePassword(user.id(), change.currentPassword(), change.newPassword());
        audit.record(user.id(), "PASSWORD_CHANGED", null, null, "ALLOWED", null);
        return session(accounts.byUsername(user.id()).orElseThrow());
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    public List<AccountService.Account> users() {
        return accounts.all();
    }

    @PostMapping("/admin/users")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public AccountService.Account create(@Valid @RequestBody NewUser request) {
        AccountService.Account created = accounts.create(request.username(), request.displayName(), request.roles(),
                request.temporaryPassword(), user.id());
        audit.record(user.id(), "USER_CREATED", null, created.username(), "ALLOWED", String.join(",", created.roles()));
        return created;
    }

    @PutMapping("/admin/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AccountService.Account update(@PathVariable String id, @Valid @RequestBody UserUpdate request) {
        AccountService.Account target = accounts.byId(id).orElseThrow(() -> new ApiErrors.NotFound("User not found"));
        if (target.username().equals(user.id()) && (!request.enabled() || !request.roles().contains(Roles.ADMIN))) {
            throw new ApiErrors.Conflict("You cannot disable your own account or remove your own administrator role");
        }
        accounts.update(id, request.displayName(), request.roles(), request.enabled());
        audit.record(user.id(), "USER_UPDATED", null, target.username(), "ALLOWED",
                String.join(",", request.roles()) + (request.enabled() ? "" : ", disabled"));
        return accounts.byId(id).orElseThrow();
    }

    @PostMapping("/admin/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public void resetPassword(@PathVariable String id, @Valid @RequestBody PasswordReset request) {
        accounts.resetPassword(id, request.temporaryPassword());
        audit.record(user.id(), "PASSWORD_RESET", null, accounts.byId(id).map(AccountService.Account::username).orElse(id), "ALLOWED", null);
    }

    @ExceptionHandler(AccountService.SignInFailed.class)
    ProblemDetail signInFailed(AccountService.SignInFailed e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    private Session session(AccountService.Account account) {
        TokenService.Issued issued = tokens.issue(account);
        return new Session(issued.token(), issued.expiresAt(), account.username(), account.displayName(),
                account.mustChangePassword() ? List.of() : account.roles(), account.mustChangePassword());
    }
}
