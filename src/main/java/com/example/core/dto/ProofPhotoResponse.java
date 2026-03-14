package com.example.core.dto;

import com.example.core.model.ProofStage;
import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class ProofPhotoResponse {
    Long id;
    Long orderId;
    Long courierId;
    ProofStage stage;
    String mimeType;
    long sizeBytes;
    String note;
    String contentUrl;
    OffsetDateTime createdAt;
}
