package com.example.core.dto.mapper;

import com.example.core.dto.OrderResponse;
import com.example.core.model.Order;
import com.example.core.model.Payment;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.PaymentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DtoMapperTest {

    @Test
    void batchMappingShouldLoadPaymentsInSingleRepositoryCall() {
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        DtoMapper mapper = new DtoMapper(paymentRepository);

        User client = User.builder()
                .id(1L)
                .name("Client")
                .phone("+79990000000")
                .password("x")
                .userRole(UserRole.CLIENT)
                .build();

        Order order1 = Order.builder().id(101L).client(client).address("A1").build();
        Order order2 = Order.builder().id(102L).client(client).address("A2").build();

        Payment payment1 = Payment.builder()
                .id(1L)
                .amount(BigDecimal.valueOf(500))
                .order(order1)
                .build();

        when(paymentRepository.findByOrderIdIn(anyCollection())).thenReturn(List.of(payment1));

        List<OrderResponse> responses = mapper.toOrderResponses(List.of(order1, order2));

        verify(paymentRepository, times(1)).findByOrderIdIn(anyCollection());
        verify(paymentRepository, never()).findByOrderId(anyLong());
        assertEquals(2, responses.size());
        assertEquals(BigDecimal.valueOf(500), responses.get(0).getPrice());
        assertNull(responses.get(1).getPrice());
    }
}
