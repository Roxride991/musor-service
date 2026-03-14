package com.example.core.dto;

import lombok.Builder;
import lombok.Value;

import java.time.OffsetDateTime;

@Value
@Builder
public class ReconciliationReportResponse {
    OffsetDateTime startedAt;
    OffsetDateTime finishedAt;
    long expiredSubscriptionsFixed;
    long overusedSubscriptionsFixed;
    long orphanSucceededPayments;
    long unpaidActiveSubscriptions;
    long totalIssues;
}
