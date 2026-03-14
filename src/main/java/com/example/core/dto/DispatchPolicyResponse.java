package com.example.core.dto;

import com.example.core.model.DispatchPolicyMode;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DispatchPolicyResponse {
    DispatchPolicyMode mode;
    int maxAssignmentsPerRun;
    int hybridLookaheadMinutes;
    boolean enabled;
}
