package com.careconnect.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/** CareConnect's own accounts: bootstrap administrator, lockout, password change, and administration. */
@SpringBootTest(properties = {
        "careconnect.auth.mode=database",
        "careconnect.auth.jwt-secret=test-only-secret-that-is-at-least-32-characters",
        "careconnect.auth.bootstrap-admin-password=First-Setup-Key-2026!"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(com.careconnect.TestSecurityConfig.class)
class AccountApiTest {

    private static final String TEMP = "Temporary-Pass-123";

    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;
    @Autowired JwtDecoder decoder;
    @Autowired DatabaseAuthConfig.DatabaseConverter converter;
    @Autowired JdbcClient jdbc;
    @Autowired ObjectMapper json;

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Object body)
            throws Exception {
        return mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private ResultActions login(String username, String password) throws Exception {
        return send(post("/api/v1/auth/login"), Map.of("username", username, "password", password));
    }

    private String token(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).path("token").asString();
    }

    @Test
    void firstAdministratorIsCreatedAndHoldsNoRolesUntilThePasswordIsChanged() throws Exception {
        ResultActions result = login("admin", "First-Setup-Key-2026!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true))
                .andExpect(jsonPath("$.roles").isEmpty());
        var auth = converter.convert(decoder.decode(token(result)));
        assertThat(auth.getName()).isEqualTo("admin");
        assertThat(auth.getAuthorities()).isEmpty();
    }

    @Test
    void wrongPasswordsLockTheAccountAndNeverSayWhichPartWasWrong() throws Exception {
        accounts.create("locked.user", "Locked User", List.of("COORDINATOR"), TEMP, "test");
        login("no.such.user", TEMP).andExpect(status().isUnauthorized());
        for (int i = 0; i < AccountService.MAX_FAILED_ATTEMPTS; i++) {
            login("locked.user", "Wrong-Password-999").andExpect(status().isUnauthorized());
        }
        login("locked.user", TEMP).andExpect(status().isUnauthorized());
        assertThat(jdbc.sql("SELECT COUNT(*) FROM audit_event WHERE action = 'SIGN_IN' AND outcome = 'DENIED' AND user_id = 'locked.user'")
                .query(Long.class).single()).isGreaterThanOrEqualTo(6);
    }

    @Test
    void changingTheTemporaryPasswordGrantsTheAccountsRoles() throws Exception {
        accounts.create("new.manager", "New Manager", List.of("MANAGER"), TEMP, "test");
        var me = user("new.manager");
        send(post("/api/v1/auth/password").with(me), Map.of("currentPassword", TEMP, "newPassword", "short"))
                .andExpect(status().isBadRequest());
        send(post("/api/v1/auth/password").with(me), Map.of("currentPassword", "Wrong-Password-999", "newPassword", "Better-Password-456"))
                .andExpect(status().isBadRequest());
        send(post("/api/v1/auth/password").with(me), Map.of("currentPassword", TEMP, "newPassword", "Better-Password-456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false))
                .andExpect(jsonPath("$.roles").value(hasItem("MANAGER")));
        String token = token(login("new.manager", "Better-Password-456").andExpect(status().isOk()));
        assertThat(converter.convert(decoder.decode(token)).getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_MANAGER");
    }

    @Test
    void onlyAdministratorsManageAccountsAndRolesAreChecked() throws Exception {
        var admin = user("admin").roles("ADMIN");
        Map<String, Object> newUser = Map.of("username", "triage.two", "displayName", "Triage Two",
                "roles", List.of("TRIAGE"), "temporaryPassword", TEMP);
        send(post("/api/v1/admin/users").with(user("someone").roles("COORDINATOR")), newUser).andExpect(status().isForbidden());
        send(post("/api/v1/admin/users").with(admin), newUser).andExpect(status().isCreated());
        send(post("/api/v1/admin/users").with(admin), newUser).andExpect(status().isConflict());
        send(post("/api/v1/admin/users").with(admin), Map.of("username", "bad.role", "displayName", "Bad",
                "roles", List.of("SUPERUSER"), "temporaryPassword", TEMP)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/admin/users").with(admin))
                .andExpect(jsonPath("$[?(@.username == 'triage.two')].displayName").value(hasItem("Triage Two")))
                .andExpect(jsonPath("$[0].passwordHash").doesNotExist());

        String triage = accounts.byUsername("triage.two").orElseThrow().id();
        send(put("/api/v1/admin/users/" + triage).with(admin), Map.of("displayName", "Triage Two", "roles", List.of("TRIAGE"), "enabled", false))
                .andExpect(status().isOk());
        login("triage.two", TEMP).andExpect(status().isUnauthorized());

        String adminId = accounts.byUsername("admin").orElseThrow().id();
        send(put("/api/v1/admin/users/" + adminId).with(admin), Map.of("displayName", "Administrator", "roles", List.of("ADMIN"), "enabled", false))
                .andExpect(status().isConflict());
    }
}
