package com.example.core.dto;

import jakarta.validation.constraints.Size;
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

    @Size(max = 500, message = "returnUrl не должен быть длиннее 500 символов")
    private String returnUrl;

    @Size(max = 255, message = "description не должен быть длиннее 255 символов")
    private String description;
}
