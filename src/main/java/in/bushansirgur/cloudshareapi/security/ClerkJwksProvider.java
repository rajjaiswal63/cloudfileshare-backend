package in.bushansirgur.cloudshareapi.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class ClerkJwksProvider {

    @Value("${clerk.jwks-url:}")
    private String jwksUrl;

    private final Map<String, PublicKey> keyCache = new ConcurrentHashMap<>();
    private volatile long lastFetchTime = 0;
    private static final long CACHE_TTL = 3600000; // 1 hour

    public PublicKey getPublicKey(String kid) throws Exception {
        if (keyCache.containsKey(kid) && (System.currentTimeMillis() - lastFetchTime < CACHE_TTL)) {
            return keyCache.get(kid);
        }

        refreshKeys();
        PublicKey key = keyCache.get(kid);
        if (key == null) {
            // Force one refresh attempt in case the key was newly rotated by Clerk
            refreshKeys();
            key = keyCache.get(kid);
        }

        if (key == null) {
            throw new IllegalArgumentException("No matching public key found for kid: " + kid);
        }
        return key;
    }

    private synchronized void refreshKeys() throws Exception {
        if (jwksUrl == null || jwksUrl.isBlank()) {
            throw new IllegalStateException("CLERK_JWKS_URL is not configured.");
        }

        log.debug("Fetching Clerk JWKS from: {}", jwksUrl);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jwks = mapper.readTree(URI.create(jwksUrl).toURL());

        JsonNode keys = jwks.get("keys");
        if (keys != null && keys.isArray()) {
            for (JsonNode keyNode : keys) {
                String kid = keyNode.path("kid").asText("");
                String kty = keyNode.path("kty").asText("");
                String alg = keyNode.path("alg").asText("");

                if ("RSA".equals(kty) && "RS256".equals(alg) && !kid.isEmpty()) {
                    String n = keyNode.path("n").asText();
                    String e = keyNode.path("e").asText();

                    PublicKey publicKey = createPublicKey(n, e);
                    keyCache.put(kid, publicKey);
                }
            }
        }
        lastFetchTime = System.currentTimeMillis();
    }

    private PublicKey createPublicKey(String modulus, String exponent) throws Exception {
        byte[] modulusBytes = Base64.getUrlDecoder().decode(modulus);
        byte[] exponentBytes = Base64.getUrlDecoder().decode(exponent);

        BigInteger modulusBigInt = new BigInteger(1, modulusBytes);
        BigInteger exponentBigInt = new BigInteger(1, exponentBytes);

        RSAPublicKeySpec spec = new RSAPublicKeySpec(modulusBigInt, exponentBigInt);
        KeyFactory factory = KeyFactory.getInstance("RSA");
        return factory.generatePublic(spec);
    }
}
