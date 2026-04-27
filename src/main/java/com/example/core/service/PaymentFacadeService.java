package com.example.core.service;

import com.example.core.dto.PageResponse;
import com.example.core.dto.PaymentCheckoutResponse;
import com.example.core.dto.PaymentInitRequest;
import com.example.core.dto.PaymentResponse;
import com.example.core.dto.PaymentWebhookRequest;
import com.example.core.exception.BadRequestException;
import com.example.core.exception.ConflictException;
import com.example.core.exception.ForbiddenOperationException;
import com.example.core.exception.ResourceNotFoundException;
import com.example.core.exception.UnauthorizedException;
import com.example.core.mapper.EntityDtoMapper;
import com.example.core.model.Order;
import com.example.core.model.Payment;
import com.example.core.model.Subscription;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.OrderRepository;
import com.example.core.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class PaymentFacadeService {

    private final PaymentService paymentService;
    private final OrderRepository orderRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EntityDtoMapper entityDtoMapper;

    public PaymentCheckoutResponse initOrderPayment(User currentUser, Long orderId, PaymentInitRequest request) {
        PaymentInitRequest safeRequest = request == null ? new PaymentInitRequest() : request;
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Заказ не найден"));

        if (!canManageOrderPayment(currentUser, order)) {
            throw new ForbiddenOperationException("Нет доступа к оплате заказа");
        }

        try {
            BigDecimal serverAmount = paymentService.resolveOrderAmount(order);
            return paymentService.initOrderPayment(
                    currentUser,
                    order,
                    serverAmount,
                    safeRequest.getReturnUrl(),
                    safeRequest.getDescription()
            );
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage(), ex);
        }
    }

    public PaymentCheckoutResponse initSubscriptionPayment(User currentUser, Long subscriptionId, PaymentInitRequest request) {
        PaymentInitRequest safeRequest = request == null ? new PaymentInitRequest() : request;
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Подписка не найдена"));

        if (!canManageSubscriptionPayment(currentUser, subscription)) {
            throw new ForbiddenOperationException("Нет доступа к оплате подписки");
        }

        try {
            BigDecimal serverAmount = paymentService.resolveSubscriptionAmount(subscription);
            return paymentService.initSubscriptionPayment(
                    currentUser,
                    subscription,
                    serverAmount,
                    safeRequest.getReturnUrl(),
                    safeRequest.getDescription()
            );
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage(), ex);
        }
    }

    public PaymentResponse syncPaymentStatus(User currentUser, Long paymentId) {
        try {
            return entityDtoMapper.toPaymentResponse(paymentService.synchronizePaymentStatus(paymentId, currentUser));
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException(ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ForbiddenOperationException(ex.getMessage());
        }
    }

    public PageResponse<PaymentResponse> listPayments(User currentUser, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 100));
        Page<Payment> payments = paymentService.getPaymentsForUser(
                currentUser,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return PageResponse.<PaymentResponse>builder()
                .content(payments.getContent().stream().map(entityDtoMapper::toPaymentResponse).toList())
                .page(payments.getNumber())
                .size(payments.getSize())
                .totalElements(payments.getTotalElements())
                .totalPages(payments.getTotalPages())
                .first(payments.isFirst())
                .last(payments.isLast())
                .build();
    }

    public PaymentResponse getPayment(User currentUser, Long paymentId) {
        try {
            return entityDtoMapper.toPaymentResponse(paymentService.getPaymentForUser(paymentId, currentUser));
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException(ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ForbiddenOperationException(ex.getMessage());
        }
    }

    public PaymentResponse handleYooKassaWebhook(PaymentWebhookRequest request, String webhookToken) {
        try {
            return entityDtoMapper.toPaymentResponse(paymentService.handleWebhook(request, webhookToken));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException(ex.getMessage(), ex);
        } catch (SecurityException ex) {
            throw new UnauthorizedException(ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ConflictException(ex.getMessage(), ex);
        }
    }

    private boolean canManageOrderPayment(User currentUser, Order order) {
        if (currentUser.getUserRole() == UserRole.ADMIN) {
            return true;
        }
        return order.getClient() != null
                && order.getClient().getId() != null
                && order.getClient().getId().equals(currentUser.getId());
    }

    private boolean canManageSubscriptionPayment(User currentUser, Subscription subscription) {
        if (currentUser.getUserRole() == UserRole.ADMIN) {
            return true;
        }
        return subscription.getUser() != null
                && subscription.getUser().getId() != null
                && subscription.getUser().getId().equals(currentUser.getId());
    }
}
