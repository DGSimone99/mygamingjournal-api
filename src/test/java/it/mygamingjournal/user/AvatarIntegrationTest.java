package it.mygamingjournal.user;

import it.mygamingjournal.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AvatarIntegrationTest extends IntegrationTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Test
    void validImageIsUploadedAndSaved() throws Exception {
        TestUser mario = register("mario");
        when(imageStorage.uploadAvatar(eq(mario.id()), any())).thenReturn("https://cdn.test/mario.png");

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/me/avatar")
                        .file(new MockMultipartFile("file", "me.png", "image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.test/mario.png"));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(jsonPath("$.avatarUrl").value("https://cdn.test/mario.png"));
    }

    @Test
    void textFilePretendingToBePngIsRejected() throws Exception {
        TestUser mario = register("mario");

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/me/avatar")
                        .file(new MockMultipartFile("file", "evil.png", "image/png",
                                "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Avatar must be a JPEG, PNG or WebP image"));
        verify(imageStorage, never()).uploadAvatar(anyLong(), any());
    }

    @Test
    void emptyFileIsRejected() throws Exception {
        TestUser mario = register("mario");

        mockMvc.perform(multipart(HttpMethod.PUT, "/api/me/avatar")
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mario)))
                .andExpect(status().isBadRequest());
    }
}
