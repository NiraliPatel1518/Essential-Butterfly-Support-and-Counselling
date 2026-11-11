package ca.sheridancollege.shtirthb.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Security rules test.
 * A small probe controller answers "ok" on the same URLs the real API uses,
 * so only the access rules in SecurityConfig are tested here.
 */
@WebMvcTest(controllers = SecurityRulesTest.ProbeController.class)
@Import({SecurityConfig.class, CorsConfig.class, SecurityRulesTest.ProbeController.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-for-security-rules-test-only-0123456789abcdef",
        "app.cors.allowed-origins=http://localhost:5173",
        "app.cors.allowed-methods=GET,POST,PUT,DELETE,OPTIONS",
        "app.cors.allowed-headers=*"
})
class SecurityRulesTest {

    @RestController
    static class ProbeController {

        @GetMapping({"/api/features", "/api/updates", "/api/updates/1",
                "/api/admin/ping", "/api/auth/me"})
        String getProbe() {
            return "ok";
        }

        @PostMapping({"/api/auth/login", "/api/newsletter/subscribe",
                "/api/admin/ping"})
        String postProbe() {
            return "ok";
        }
    }

    @Autowired
    private MockMvc mockMvc;

    // ---------- public URLs: no token needed ----------

    @Test
    void publicFeatureList_isOpenWithoutToken() throws Exception {
        mockMvc.perform(get("/api/features")).andExpect(status().isOk());
    }

    @Test
    void publicUpdateList_isOpenWithoutToken() throws Exception {
        mockMvc.perform(get("/api/updates")).andExpect(status().isOk());
    }

    @Test
    void login_isOpenWithoutToken() throws Exception {
        mockMvc.perform(post("/api/auth/login")).andExpect(status().isOk());
    }

    @Test
    void newsletterSubscribe_isOpenWithoutToken() throws Exception {
        mockMvc.perform(post("/api/newsletter/subscribe")).andExpect(status().isOk());
    }

    // ---------- login required ----------

    @Test
    void updateDetails_needLogin() throws Exception {
        mockMvc.perform(get("/api/updates/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void updateDetails_workWithLogin() throws Exception {
        mockMvc.perform(get("/api/updates/1")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
                .andExpect(status().isOk());
    }

    @Test
    void currentUser_needsLogin() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    // ---------- admin only ----------

    @Test
    void adminUrl_withoutToken_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/ping")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminUrl_withClientToken_isForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminUrl_withAdminToken_isAllowed() throws Exception {
        mockMvc.perform(get("/api/admin/ping")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void adminPost_withClientToken_isForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/ping")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CLIENT"))))
                .andExpect(status().isForbidden());
    }
}