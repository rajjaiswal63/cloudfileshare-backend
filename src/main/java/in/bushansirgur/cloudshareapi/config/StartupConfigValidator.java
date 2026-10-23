package in.bushansirgur.cloudshareapi.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class StartupConfigValidator implements ApplicationRunner {

    @Value("${server.port:8080}")
    private String serverPort;

    @Value("${server.servlet.context-path:/api/v1.0}")
    private String contextPath;

    @Value("${spring.data.mongodb.uri:}")
    private String mongoUri;

    @Value("${clerk.issuer:}")
    private String clerkIssuer;

    @Value("${clerk.jwks-url:}")
    private String clerkJwksUrl;

    @Value("${clerk.webhook.secret:}")
    private String clerkWebhookSecret;

    @Value("${razorpay.key.id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key.secret:}")
    private String razorpayKeySecret;

    @Value("${cors.allowed.origins:}")
    private String allowedOrigins;

    @Value("${file.upload.dir:upload}")
    private String uploadDir;

    @Override
    public void run(ApplicationArguments args) {
        log.info("==================================================");
        log.info("  CloudFileShare API Initializing");
        log.info("  Server Port: {}", serverPort);
        log.info("  Context Path: {}", contextPath);
        log.info("  Upload Directory: {}", uploadDir);
        log.info("  CORS Allowed Origins: {}", allowedOrigins);

        // Sanitize MongoDB URI for logging (NEVER print passwords or full credentials)
        if (mongoUri == null || mongoUri.isBlank()) {
            log.error("[CONFIG ERROR] MONGODB_URI is not set! Database connection will fail.");
        } else {
            String sanitizedUri = sanitizeMongoUri(mongoUri);
            log.info("  MongoDB URI: {}", sanitizedUri);
            if (mongoUri.contains("localhost") && System.getenv("RENDER") != null) {
                log.warn("[CONFIG WARNING] Detected localhost MongoDB URI in a Render environment. Set MONGODB_URI to your MongoDB Atlas connection string in Render dashboard.");
            }
        }

        // Validate Clerk configuration
        List<String> missingClerk = new ArrayList<>();
        if (clerkIssuer == null || clerkIssuer.isBlank()) {
            missingClerk.add("CLERK_ISSUER");
        } else {
            log.info("  Clerk Issuer: {}", clerkIssuer);
        }

        if (clerkJwksUrl == null || clerkJwksUrl.isBlank()) {
            missingClerk.add("CLERK_JWKS_URL");
        } else {
            log.info("  Clerk JWKS URL: {}", clerkJwksUrl);
        }

        if (!missingClerk.isEmpty()) {
            log.warn("[CONFIG WARNING] Missing Clerk configuration: {}. Authenticated endpoints will reject requests until configured in Render dashboard.", missingClerk);
        }

        if (clerkWebhookSecret == null || clerkWebhookSecret.isBlank()) {
            log.info("  Clerk Webhook Secret: NOT_SET (webhook verification is disabled/optional)");
        } else {
            log.info("  Clerk Webhook Secret: CONFIGURED");
        }

        // Validate Razorpay configuration
        if (razorpayKeyId == null || razorpayKeyId.isBlank() || razorpayKeySecret == null || razorpayKeySecret.isBlank()) {
            log.info("  Razorpay Integration: NOT CONFIGURED (Payment endpoints will be unavailable until RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET are set)");
        } else {
            log.info("  Razorpay Integration: CONFIGURED (Key ID: {})", razorpayKeyId);
        }

        log.info("==================================================");
    }

    private String sanitizeMongoUri(String uri) {
        try {
            if (uri.contains("@")) {
                int protocolEnd = uri.indexOf("://");
                int atIndex = uri.indexOf("@");
                if (protocolEnd != -1 && atIndex > protocolEnd) {
                    String prefix = uri.substring(0, protocolEnd + 3);
                    String userPass = uri.substring(protocolEnd + 3, atIndex);
                    String remainder = uri.substring(atIndex);
                    if (userPass.contains(":")) {
                        String user = userPass.substring(0, userPass.indexOf(":"));
                        return prefix + user + ":*****" + remainder;
                    }
                    return prefix + "*****" + remainder;
                }
            }
            return uri;
        } catch (Exception e) {
            return "<masked-uri>";
        }
    }
}
