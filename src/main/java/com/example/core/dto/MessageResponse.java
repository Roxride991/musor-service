package com.example.core.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class MessageResponse {
    String message;
}
