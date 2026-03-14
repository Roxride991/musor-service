package com.example.core.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class AddressSuggestionResponse {
    private String address;
    private Double lat;
    private Double lng;
}
