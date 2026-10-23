package in.bushansirgur.cloudshareapi.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClerkJwtAuthFilter extends OncePerRequestFilter {

    @Value("${clerk.issuer:}")
    private String clerkIssuer;

    private final ClerkJwksProvider jwksProvider;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // Allow preflight CORS requests to proceed without authentication
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String uri = request.getRequestURI();
        boolean isPublicEndpoint = uri.contains("/webhooks") ||
                uri.contains("/files/public") ||
                uri.contains("/health");

        String authHeader = request.getHeader("Authorization");

        // If this is a public endpoint or unauthenticated download, allow it through without token
        if ((isPublicEndpoint || uri.contains("/files/download")) && (authHeader == null || !authHeader.startsWith("Bearer "))) {
            filterChain.doFilter(request, response);
            return;
        }

        // For protected endpoints, enforce Bearer header presence
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Authorization header missing or invalid");
            return;
        }

        // Validate that CLERK_ISSUER is configured
        if (clerkIssuer == null || clerkIssuer.isBlank()) {
            log.error("CLERK_ISSUER is not configured. Rejecting authentication request.");
            sendJsonError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Service Unavailable", "Authentication server configuration (CLERK_ISSUER) is missing");
            return;
        }

        try {
            String token = authHeader.substring(7).trim();
            String[] chunks = token.split("\\.");
            if (chunks.length < 3) {
                sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Invalid JWT token format");
                return;
            }

            String headerJson = new String(Base64.getUrlDecoder().decode(chunks[0]));
            JsonNode headerNode = mapper.readTree(headerJson);

            if (!headerNode.has("kid")) {
                sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Token header is missing 'kid'");
                return;
            }

            String kid = headerNode.get("kid").asText();
            PublicKey publicKey = jwksProvider.getPublicKey(kid);

            // Verify the token
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .setAllowedClockSkewSeconds(60)
                    .requireIssuer(clerkIssuer)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            String clerkId = claims.getSubject();
            if (clerkId == null || clerkId.isBlank()) {
                sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Token subject (clerkId) is missing");
                return;
            }

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    clerkId,
                    null,
                    Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"))
            );

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.warn("JWT authentication failed: {}", e.getMessage());
            sendJsonError(response, HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized", "Invalid or expired JWT token");
        }
    }

    private void sendJsonError(HttpServletResponse response, int status, String error, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String json = String.format("{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\"}", status, escapeJson(error), escapeJson(message));
        response.getWriter().write(json);
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\"", "\\\"").replace("\n", " ").replace("\r", "");
    }
}
