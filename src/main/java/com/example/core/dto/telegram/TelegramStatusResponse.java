package com.example.core.dto.telegram;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TelegramStatusResponse {
    @JsonProperty("session_id")
    String sessionId;

    String status;

    @JsonProperty("expires_at")
    String expiresAt;

    @JsonProperty("verified_at")
    String verifiedAt;

    boolean authenticated;

    boolean consumed;

    String token;

    Map<String, Object> user;

    String message;
}
