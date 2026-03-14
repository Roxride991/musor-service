package com.example.core.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class TelegramInitResponse {
    @JsonProperty("session_id")
    String sessionId;

    String status;

    @JsonProperty("expires_at")
    String expiresAt;

    @JsonProperty("ttl_seconds")
    long ttlSeconds;

    @JsonProperty("start_url")
    String startUrl;
}
