package com.example.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class PaymentInitRequest {

    /**
     * Deprecated input: amount is calculated on the server side.
     * Left for backward compatibility with old clients.
     */
    private BigDecimal amount;

    private String returnUrl;

    private String description;
}
