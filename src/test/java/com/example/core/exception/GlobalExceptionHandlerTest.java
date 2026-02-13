package com.example.core.exception;

import com.example.core.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    @Test
    void validationHandlerShouldSupportObjectAndFieldErrors() throws Exception {
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "testDto");
        bindingResult.addError(new ObjectError("testDto", "Общая ошибка"));
        bindingResult.addError(new FieldError("testDto", "phone", "Неверный формат"));

        MethodParameter methodParameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", Object.class), 0
        );
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ResponseEntity<ErrorResponse> response = handler.handleValidationExceptions(ex);

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getDetails());
        assertTrue(response.getBody().getDetails().containsKey("testDto"));
        assertTrue(response.getBody().getDetails().containsKey("phone"));
    }

    @SuppressWarnings("unused")
    private void dummyMethod(Object body) {
    }
}
