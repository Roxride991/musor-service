package com.example.core.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ClientIpResolver {

    private static final String UNKNOWN = "unknown";
    private final boolean trustForwardHeaders;
    private final Set<String> trustedProxies;

    public ClientIpResolver(
            @Value("${security.trust-forward-headers:false}") boolean trustForwardHeaders,
            @Value("${security.trusted-proxies:127.0.0.1,::1}") String trustedProxiesRaw
    ) {
        this.trustForwardHeaders = trustForwardHeaders;
        this.trustedProxies = Arrays.stream(trustedProxiesRaw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toSet());
    }

    public String resolve(HttpServletRequest request) {
        if (request == null) {
            return UNKNOWN;
        }

        String remoteAddr = normalize(request.getRemoteAddr());
        if (trustForwardHeaders && isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String first = normalize(forwarded.split(",")[0]);
                if (!UNKNOWN.equals(first)) {
                    return first;
                }
            }

            String realIp = normalize(request.getHeader("X-Real-IP"));
            if (!UNKNOWN.equals(realIp)) {
                return realIp;
            }
        }

        return remoteAddr;
    }

    private boolean isTrustedProxy(String remoteAddr) {
        return !UNKNOWN.equals(remoteAddr) && trustedProxies.contains(remoteAddr);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String normalized = value.trim();
        if (normalized.startsWith("[")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("]")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? UNKNOWN : normalized;
    }
}
