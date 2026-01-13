package com.ri.orchestrator.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ServiceTokenProvider {

    private static final long MIN_TTL_SECONDS = 60;
    private final ObjectMapper objectMapper;
    private final String secret;
    private final String issuer;
    private final String audience;
    private final String subject;
    private final List<String> roles;
    private final long ttlSeconds;
    private String cachedToken;
    private long cachedExpEpochSeconds;

    public ServiceTokenProvider(
            ObjectMapper objectMapper,
            @Value("${aws.backend.service-secret:}") String secret,
            @Value("${aws.backend.service-issuer:}") String issuer,
            @Value("${aws.backend.service-audience:}") String audience,
            @Value("${aws.backend.service-subject:ri-orchestrator}") String subject,
            @Value("${aws.backend.service-roles:service}") List<String> roles,
            @Value("${aws.backend.service-ttl-seconds:3600}") long ttlSeconds) {
        this.objectMapper = objectMapper;
        this.secret = secret;
        this.issuer = issuer;
        this.audience = audience;
        this.subject = subject;
        this.roles = roles == null ? List.of() : roles;
        this.ttlSeconds = Math.max(ttlSeconds, MIN_TTL_SECONDS);
    }

    public synchronized String getToken() {
        long now = Instant.now().getEpochSecond();
        if (cachedToken != null && now < cachedExpEpochSeconds - 30) {
            return cachedToken;
        }
        cachedToken = generateToken(now);
        return cachedToken;
    }

    private String generateToken(long now) {
        if (!StringUtils.hasText(secret)) {
            throw new IllegalStateException("Missing aws.backend.service-secret");
        }
        long exp = now + ttlSeconds;
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", subject);
        payload.put("iat", now);
        payload.put("exp", exp);
        if (StringUtils.hasText(issuer)) {
            payload.put("iss", issuer);
        }
        if (StringUtils.hasText(audience)) {
            payload.put("aud", List.of(audience));
        }
        if (!roles.isEmpty()) {
            payload.put("roles", roles);
            payload.put("role", roles.get(0));
        }

        String encodedHeader = base64Url(json(header));
        String encodedPayload = base64Url(json(payload));
        String signature = sign(encodedHeader + "." + encodedPayload);
        cachedExpEpochSeconds = exp;
        return encodedHeader + "." + encodedPayload + "." + signature;
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize JWT payload", e);
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] signature = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return base64Url(signature);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT", e);
        }
    }

    private String base64Url(String value) {
        return base64Url(value.getBytes(StandardCharsets.UTF_8));
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
