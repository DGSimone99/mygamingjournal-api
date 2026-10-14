package it.mygamingjournal.auth;

import it.mygamingjournal.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    private static String registerBody(String username, String email) {
        return """
                {"username":"%s","email":"%s","password":"password123"}
                """.formatted(username, email);
    }

    private static String loginBody(String usernameOrEmail, String password) {
        return """
                {"usernameOrEmail":"%s","password":"%s"}
                """.formatted(usernameOrEmail, password);
    }

    @Test
    void registerReturnsTokenAndUser() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("mario", "mario@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").value(not(emptyString())))
                .andExpect(jsonPath("$.user.username").value("mario"))
                .andExpect(jsonPath("$.user.displayName").value("mario"))
                .andExpect(jsonPath("$.user.level").value(0));
    }

    @Test
    void usernameIsUniqueIgnoringCase() throws Exception {
        register("mario");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("Mario", "other@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Username already taken"));
    }

    @Test
    void emailIsUniqueIgnoringCase() throws Exception {
        register("mario");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("luigi", "MARIO@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Email already registered"));
    }

    @Test
    void invalidRegistrationListsEveryBadField() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"a\",\"email\":\"nope\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)));
    }

    @Test
    void passwordOver72BytesIs400NotACrash() throws Exception {
        // 40 characters but 80 UTF-8 bytes: BCrypt only accepts 72 bytes.
        String password = "è".repeat(40);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"mario\",\"email\":\"mario@example.com\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Password must be at most 72 bytes"));
    }

    @Test
    void loginAcceptsUsernameOrEmailInAnyCase() throws Exception {
        register("mario");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("MARIO", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("mario"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("Mario@Example.com", "password123")))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        register("mario");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("mario", "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid credentials"));
    }

    @Test
    void protectedEndpointWithoutTokenIs401ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("Authentication required"));
    }

    @Test
    void protectedEndpointWithBadTokenIs401() throws Exception {
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }
}
