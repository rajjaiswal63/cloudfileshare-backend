package in.bushansirgur.cloudshareapi;

import in.bushansirgur.cloudshareapi.controller.HealthController;
import in.bushansirgur.cloudshareapi.repository.FileMetadataRepository;
import in.bushansirgur.cloudshareapi.repository.PaymentTransactionRepository;
import in.bushansirgur.cloudshareapi.repository.ProfileRepository;
import in.bushansirgur.cloudshareapi.repository.UserCreditsRepository;
import in.bushansirgur.cloudshareapi.security.ClerkJwksProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.data.mongodb.uri=mongodb://localhost:27017/testdb",
        "clerk.issuer=https://test.clerk.accounts.dev",
        "clerk.jwks-url=https://test.clerk.accounts.dev/.well-known/jwks.json",
        "cors.allowed.origins=https://cloudfileshare.netlify.app,http://localhost:5173",
        "file.upload.dir=target/test-uploads"
})
public class SecurityAndHealthTest {

    @Autowired
    private MockMvc mockMvc;

    // Mock Mongo repositories so test does not require a running MongoDB server
    @MockBean
    private FileMetadataRepository fileMetadataRepository;

    @MockBean
    private ProfileRepository profileRepository;

    @MockBean
    private UserCreditsRepository userCreditsRepository;

    @MockBean
    private PaymentTransactionRepository paymentTransactionRepository;

    @MockBean
    private ClerkJwksProvider clerkJwksProvider;

    @Test
    public void healthEndpoint_isPublicAndReturns200() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Application is up and running - v1.0"));
    }

    @Test
    public void corsPreflight_toProtectedEndpoint_allowedWithoutAuth() throws Exception {
        mockMvc.perform(options("/files/my")
                        .header(HttpHeaders.ORIGIN, "https://cloudfileshare.netlify.app")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization,Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://cloudfileshare.netlify.app"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    public void protectedEndpoint_withoutToken_returns401Unauthorized() throws Exception {
        mockMvc.perform(get("/files/my"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }
}
