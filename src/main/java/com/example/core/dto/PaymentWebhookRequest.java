package com.example.core.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PaymentWebhookRequest {
    private String event;
    private JsonNode object;
}
