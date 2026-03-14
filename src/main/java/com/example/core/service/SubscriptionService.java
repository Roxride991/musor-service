package com.example.core.service;

import com.example.core.model.PickupSlot;
import com.example.core.model.ServiceZone;
import com.example.core.model.Subscription;
import com.example.core.model.SubscriptionPlan;
import com.example.core.model.SubscriptionStatus;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.ServiceZoneRepository;
import com.example.core.repository.SubscriptionRepository;
import com.example.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Сервис подписок: создание, получение, отмена, пауза, возобновление и редактирование графика.
 */
@Service
@RequiredArgsConstructor
public class SubscriptionService {

    private static final double MAX_PAUSE_RATIO = 0.15;
    private static final int MIN_PAUSE_DAYS_LIMIT = 1;

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    private final ServiceZoneRepository zoneRepository;
    private final GeoUtils geoUtils;
    private final GeocodingService geocodingService;
    private final SubscriptionSchedulingService schedulingService;
    private final AuditService auditService;

    @Transactional
    public Subscription createSubscription(
            User user,
            SubscriptionPlan plan,
            String address,
            PickupSlot pickupSlot,
            LocalDate requestedStartDate,
            Double lat,
            Double lng,
            Integer requestedCadenceDays
    ) {
        User lockedUser = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));

        if (lockedUser.getUserRole() != UserRole.CLIENT) {
            throw new IllegalStateException("Только клиенты могут оформлять подписки");
        }

        LocalDate today = LocalDate.now();
        boolean hasActiveOrPaused = subscriptionRepository.findByUserId(lockedUser.getId()).stream()
                .anyMatch(s ->
                        (s.getStatus() == SubscriptionStatus.ACTIVE && !s.getEndDate().isBefore(today))
                                || s.getStatus() == SubscriptionStatus.PAUSED
                );
        if (hasActiveOrPaused) {
            throw new IllegalStateException("У вас уже есть активная или приостановленная подписка");
        }

        LocalDate startDate = resolveStartDate(requestedStartDate, today);
        LocalDate endDate = calculateEndDate(startDate, plan);
        int cadenceDays = resolveCadenceDays(requestedCadenceDays);
        int totalAllowedOrders = schedulingService.calculateTotalAllowedOrders(startDate, endDate, cadenceDays);
        if (totalAllowedOrders <= 0) {
            throw new IllegalStateException("Не удалось рассчитать график подписки");
        }

        ServiceZone.Coordinate coordinate = resolveAndValidateCoordinates(address, lat, lng);

        Subscription subscription = Subscription.builder()
                .user(lockedUser)
                .plan(plan)
                .startDate(startDate)
                .endDate(endDate)
                .price(calculatePrice(plan, cadenceDays))
                .status(SubscriptionStatus.ACTIVE)
                .totalAllowedOrders(totalAllowedOrders)
                .usedOrders(0)
                .serviceAddress(address.trim())
                .serviceLat(coordinate.getLat())
                .serviceLng(coordinate.getLng())
                .pickupSlot(pickupSlot)
                .cadenceDays(cadenceDays)
                .nextPickupAt(null)
                .build();

        Subscription saved = subscriptionRepository.save(subscription);
        schedulingService.scheduleNextOrderIfNeeded(saved.getId());
        auditService.log(
                "SUBSCRIPTION_CREATE",
                "SUCCESS",
                lockedUser,
                "SUBSCRIPTION_ID",
                String.valueOf(saved.getId()),
                "Subscription created: plan=" + plan + ", cadenceDays=" + cadenceDays,
                null
        );
        return subscriptionRepository.findById(saved.getId()).orElse(saved);
    }

    /** Продлевает активную/приостановленную подписку на срок выбранного плана. */
    @Transactional
    public Subscription extendSubscription(Long subscriptionId, User currentUser, SubscriptionPlan requestedPlan) {
        Subscription subscription = getOwnedSubscription(subscriptionId, currentUser);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE && subscription.getStatus() != SubscriptionStatus.PAUSED) {
            throw new IllegalStateException("Продлить можно только активную или приостановленную подписку");
        }

        SubscriptionPlan extensionPlan = requestedPlan == null ? subscription.getPlan() : requestedPlan;
        int cadenceDays = Math.max(1, subscription.getCadenceDays());

        LocalDate today = LocalDate.now();
        LocalDate extensionStart = subscription.getEndDate().isBefore(today)
                ? today
                : subscription.getEndDate().plusDays(1);
        LocalDate extensionEnd = calculateEndDate(extensionStart, extensionPlan);
        int additionalOrders = schedulingService.calculateTotalAllowedOrders(extensionStart, extensionEnd, cadenceDays);
        if (additionalOrders <= 0) {
            throw new IllegalStateException("Не удалось рассчитать продление подписки");
        }

        subscription.setPlan(extensionPlan);
        subscription.setEndDate(extensionEnd);
        subscription.setTotalAllowedOrders(subscription.getTotalAllowedOrders() + additionalOrders);
        subscription.setPrice(subscription.getPrice().add(calculatePrice(extensionPlan, cadenceDays)));
        subscriptionRepository.save(subscription);
        auditService.log(
                "SUBSCRIPTION_EXTEND",
                "SUCCESS",
                currentUser,
                "SUBSCRIPTION_ID",
                String.valueOf(subscription.getId()),
                "Extended with plan=" + extensionPlan + ", additionalOrders=" + additionalOrders,
                null
        );

        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            schedulingService.scheduleNextOrderIfNeeded(subscription.getId());
        }
        return subscriptionRepository.findById(subscriptionId).orElse(subscription);
    }

    /** Возвращает все подписки пользователя. */
    public List<Subscription> getSubscriptionsForUser(User user) {
        return subscriptionRepository.findByUserId(user.getId());
    }

    /** Возвращает подписки, доступные для управления: ACTIVE и PAUSED. */
    public List<Subscription> getActiveSubscriptionsForUser(User user) {
        LocalDate now = LocalDate.now();
        return subscriptionRepository.findByUserId(user.getId()).stream()
                .filter(s -> {
                    if (s.getStatus() == SubscriptionStatus.PAUSED) {
                        return true;
                    }
                    return s.getStatus() == SubscriptionStatus.ACTIVE
                            && s.getEndDate() != null
                            && !s.getEndDate().isBefore(now);
                })
                .toList();
    }

    public Subscription getSubscriptionForTimeline(Long subscriptionId, User currentUser) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));

        if (currentUser.getUserRole() == UserRole.ADMIN) {
            return subscription;
        }
        if (currentUser.getUserRole() == UserRole.CLIENT
                && subscription.getUser() != null
                && subscription.getUser().getId() != null
                && subscription.getUser().getId().equals(currentUser.getId())) {
            return subscription;
        }
        throw new IllegalStateException("Подписка недоступна для просмотра");
    }

    /** Отменяет подписку немедленно (статус CANCELED). */
    @Transactional
    public void cancelSubscription(Long subscriptionId, User currentUser) {
        Subscription subscription = getOwnedSubscription(subscriptionId, currentUser);
        if (subscription.getStatus() == SubscriptionStatus.CANCELED || subscription.getStatus() == SubscriptionStatus.EXPIRED) {
            throw new IllegalStateException("Подписка уже завершена");
        }

        schedulingService.cancelUpcomingPublishedOrder(subscriptionId);
        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscription.setPauseStartedAt(null);
        subscriptionRepository.save(subscription);
        auditService.log(
                "SUBSCRIPTION_CANCEL",
                "SUCCESS",
                currentUser,
                "SUBSCRIPTION_ID",
                String.valueOf(subscription.getId()),
                "Subscription canceled",
                null
        );
    }

    /** Приостанавливает активную подписку (статус PAUSED). */
    @Transactional
    public Subscription pauseSubscription(Long subscriptionId, User currentUser) {
        Subscription subscription = getOwnedSubscription(subscriptionId, currentUser);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Можно приостановить только активную подписку");
        }

        int maxPauseDays = calculateMaxPauseDays(subscription);
        int usedPauseDays = Math.max(0, subscription.getPausedDaysUsed());
        if (usedPauseDays >= maxPauseDays) {
            throw new IllegalStateException(
                    "Лимит паузы исчерпан: " + usedPauseDays + " из " + maxPauseDays + " дней"
            );
        }

        schedulingService.cancelUpcomingPublishedOrder(subscriptionId);
        subscription.setStatus(SubscriptionStatus.PAUSED);
        subscription.setPauseStartedAt(OffsetDateTime.now());
        Subscription saved = subscriptionRepository.save(subscription);
        auditService.log(
                "SUBSCRIPTION_PAUSE",
                "SUCCESS",
                currentUser,
                "SUBSCRIPTION_ID",
                String.valueOf(saved.getId()),
                "Subscription paused",
                null
        );
        return saved;
    }

    /** Возобновляет приостановленную подписку (статус ACTIVE). */
    @Transactional
    public Subscription resumeSubscription(Long subscriptionId, User currentUser) {
        Subscription subscription = getOwnedSubscription(subscriptionId, currentUser);
        if (subscription.getStatus() != SubscriptionStatus.PAUSED) {
            throw new IllegalStateException("Можно возобновить только приостановленную подписку");
        }

        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime pauseStartedAt = subscription.getPauseStartedAt();
        int rawPausedDays = calculatePausedDays(pauseStartedAt, now);
        int maxPauseDays = calculateMaxPauseDays(subscription);
        int usedPauseDays = Math.max(0, subscription.getPausedDaysUsed());
        int remainingPauseDays = Math.max(0, maxPauseDays - usedPauseDays);
        int effectivePausedDays = Math.min(rawPausedDays, remainingPauseDays);

        if (effectivePausedDays > 0) {
            subscription.setPausedDaysUsed(usedPauseDays + effectivePausedDays);
            subscription.setEndDate(subscription.getEndDate().plusDays(effectivePausedDays));
            if (subscription.getNextPickupAt() != null) {
                subscription.setNextPickupAt(subscription.getNextPickupAt().plusDays(effectivePausedDays));
            }
        }

        subscription.setPauseStartedAt(null);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscriptionRepository.save(subscription);
        schedulingService.scheduleNextOrderIfNeeded(subscription.getId());
        auditService.log(
                "SUBSCRIPTION_RESUME",
                "SUCCESS",
                currentUser,
                "SUBSCRIPTION_ID",
                String.valueOf(subscription.getId()),
                "Subscription resumed. Added pauseDays=" + effectivePausedDays,
                null
        );
        return subscriptionRepository.findById(subscription.getId()).orElse(subscription);
    }

    /** Пропускает ближайший вывоз и переносит график на следующий шаг cadence. */
    @Transactional
    public Subscription skipNextPickup(Long subscriptionId, User currentUser) {
        Subscription subscription = getOwnedSubscription(subscriptionId, currentUser);
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException("Пропустить следующий вывоз можно только для активной подписки");
        }
        Subscription saved = schedulingService.skipNextPickup(subscriptionId);
        auditService.log(
                "SUBSCRIPTION_SKIP_NEXT",
                "SUCCESS",
                currentUser,
                "SUBSCRIPTION_ID",
                String.valueOf(saved.getId()),
                "Next pickup skipped",
                null
        );
        return saved;
    }

    /** Обновляет адрес подписки и пересоздаёт ближайший автозаказ для активной подписки. */
    @Transactional
    public Subscription updateAddress(
            Long subscriptionId,
            User currentUser,
            String address,
            Double lat,
            Double lng
    ) {
        Subscription subscription = getOwnedSubscription(subscriptionId, currentUser);
        ensureEditableStatus(subscription);

        ServiceZone.Coordinate coordinate = resolveAndValidateCoordinates(address, lat, lng);
        subscription.setServiceAddress(address.trim());
        subscription.setServiceLat(coordinate.getLat());
        subscription.setServiceLng(coordinate.getLng());

        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            OffsetDateTime cancelledPickup = schedulingService.cancelUpcomingPublishedOrderAndGetPickupTime(subscriptionId);
            if (cancelledPickup != null) {
                subscription.setNextPickupAt(alignToSlot(cancelledPickup.toLocalDate(), subscription.getPickupSlot()));
            }
        }

        subscriptionRepository.save(subscription);
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            schedulingService.scheduleNextOrderIfNeeded(subscriptionId);
        }
        auditService.log(
                "SUBSCRIPTION_UPDATE_ADDRESS",
                "SUCCESS",
                currentUser,
                "SUBSCRIPTION_ID",
                String.valueOf(subscription.getId()),
                "Subscription address updated",
                null
        );
        return subscriptionRepository.findById(subscriptionId).orElse(subscription);
    }

    /** Обновляет слот подписки и пересоздаёт ближайший автозаказ для активной подписки. */
    @Transactional
    public Subscription updatePickupSlot(Long subscriptionId, User currentUser, PickupSlot pickupSlot) {
        Subscription subscription = getOwnedSubscription(subscriptionId, currentUser);
        ensureEditableStatus(subscription);

        subscription.setPickupSlot(pickupSlot);

        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            OffsetDateTime cancelledPickup = schedulingService.cancelUpcomingPublishedOrderAndGetPickupTime(subscriptionId);
            if (cancelledPickup != null) {
                subscription.setNextPickupAt(alignToSlot(cancelledPickup.toLocalDate(), pickupSlot));
            } else if (subscription.getNextPickupAt() != null) {
                LocalDate nextDate = subscription.getNextPickupAt().toLocalDate();
                subscription.setNextPickupAt(alignToSlot(nextDate, pickupSlot));
            }
        } else if (subscription.getNextPickupAt() != null) {
            LocalDate nextDate = subscription.getNextPickupAt().toLocalDate();
            subscription.setNextPickupAt(alignToSlot(nextDate, pickupSlot));
        }

        subscriptionRepository.save(subscription);
        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            schedulingService.scheduleNextOrderIfNeeded(subscriptionId);
        }
        auditService.log(
                "SUBSCRIPTION_UPDATE_SLOT",
                "SUCCESS",
                currentUser,
                "SUBSCRIPTION_ID",
                String.valueOf(subscription.getId()),
                "Subscription slot updated to " + pickupSlot,
                null
        );
        return subscriptionRepository.findById(subscriptionId).orElse(subscription);
    }

    /** Гибкий перенос ближайшего вывоза с возможной сменой слота по бизнес-правилам. */
    @Transactional
    public Subscription rescheduleNextPickup(
            Long subscriptionId,
            User currentUser,
            LocalDate requestedDate,
            PickupSlot requestedSlot,
            String reason
    ) {
        Subscription subscription = getOwnedSubscription(subscriptionId, currentUser);
        ensureEditableStatus(subscription);

        PickupSlot slot = requestedSlot == null ? subscription.getPickupSlot() : requestedSlot;
        if (slot == null) {
            throw new IllegalArgumentException("Для переноса необходимо указать временной слот");
        }

        LocalDate today = LocalDate.now();
        LocalDate baseDate = requestedDate;
        if (baseDate == null) {
            if (subscription.getNextPickupAt() != null) {
                baseDate = subscription.getNextPickupAt().toLocalDate();
            } else {
                baseDate = today;
            }
        }
        if (baseDate.isBefore(today)) {
            baseDate = today;
        }

        OffsetDateTime candidate = alignToSlot(baseDate, slot);
        OffsetDateTime minAllowed = OffsetDateTime.now().plusHours(1);
        if (candidate.isBefore(minAllowed)) {
            candidate = alignToSlot(minAllowed.toLocalDate().plusDays(1), slot);
        }

        if (candidate.toLocalDate().isAfter(subscription.getEndDate())) {
            throw new IllegalStateException("Нельзя перенести вывоз за пределы срока подписки");
        }

        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            schedulingService.cancelUpcomingPublishedOrder(subscriptionId);
        }

        subscription.setPickupSlot(slot);
        subscription.setNextPickupAt(candidate);
        subscriptionRepository.save(subscription);

        if (subscription.getStatus() == SubscriptionStatus.ACTIVE) {
            schedulingService.scheduleNextOrderIfNeeded(subscriptionId);
        }

        auditService.log(
                "SUBSCRIPTION_RESCHEDULE",
                "SUCCESS",
                currentUser,
                "SUBSCRIPTION_ID",
                String.valueOf(subscription.getId()),
                "Rescheduled next pickup to " + candidate + ", slot=" + slot + ", reason=" + normalizeReason(reason),
                null
        );

        return subscriptionRepository.findById(subscriptionId).orElse(subscription);
    }

    private Subscription getOwnedSubscription(Long subscriptionId, User currentUser) {
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Подписка не найдена"));

        if (!subscription.getUser().getId().equals(currentUser.getId())) {
            throw new IllegalStateException("Подписка не принадлежит вам");
        }
        return subscription;
    }

    private void ensureEditableStatus(Subscription subscription) {
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE && subscription.getStatus() != SubscriptionStatus.PAUSED) {
            throw new IllegalStateException("Изменения доступны только для активной или приостановленной подписки");
        }
    }

    private int calculateMaxPauseDays(Subscription subscription) {
        if (subscription.getStartDate() == null || subscription.getEndDate() == null) {
            return MIN_PAUSE_DAYS_LIMIT;
        }
        long totalDays = ChronoUnit.DAYS.between(subscription.getStartDate(), subscription.getEndDate()) + 1L;
        if (totalDays <= 0) {
            return MIN_PAUSE_DAYS_LIMIT;
        }
        int calculated = (int) Math.floor(totalDays * MAX_PAUSE_RATIO);
        return Math.max(MIN_PAUSE_DAYS_LIMIT, calculated);
    }

    private int calculatePausedDays(OffsetDateTime pauseStartedAt, OffsetDateTime now) {
        if (pauseStartedAt == null || now == null || now.isBefore(pauseStartedAt)) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(pauseStartedAt.toLocalDate(), now.toLocalDate());
        return (int) Math.max(0L, days);
    }

    private LocalDate resolveStartDate(LocalDate requestedStartDate, LocalDate today) {
        if (requestedStartDate == null) {
            return today;
        }
        if (requestedStartDate.isBefore(today)) {
            return today;
        }
        return requestedStartDate;
    }

    private LocalDate calculateEndDate(LocalDate startDate, SubscriptionPlan plan) {
        int durationDays = switch (plan) {
            case WEEKLY -> 14;
            case MONTHLY -> 30;
            case QUARTERLY -> 90;
            case YEARLY -> 364;
        };
        return startDate.plusDays(durationDays - 1L);
    }

    private int resolveCadenceDays(Integer requestedCadenceDays) {
        if (requestedCadenceDays == null) {
            return 2;
        }
        if (requestedCadenceDays < 1 || requestedCadenceDays > 2) {
            throw new IllegalArgumentException("Интервал подписки может быть только 1 или 2 дня");
        }
        return requestedCadenceDays;
    }

    private BigDecimal calculatePrice(SubscriptionPlan plan, int cadenceDays) {
        BigDecimal basePrice = plan.getPrice();
        return cadenceDays <= 1 ? basePrice.multiply(BigDecimal.valueOf(2)) : basePrice;
    }

    private ServiceZone.Coordinate resolveAndValidateCoordinates(String address, Double lat, Double lng) {
        String normalizedAddress = address == null ? "" : address.trim();
        if (normalizedAddress.isEmpty()) {
            throw new IllegalArgumentException("Адрес обязателен");
        }

        ServiceZone.Coordinate coordinate;
        if (lat != null && lng != null) {
            coordinate = new ServiceZone.Coordinate(lat, lng);
        } else {
            coordinate = geocodingService.getCoordinates(normalizedAddress);
        }

        validateAddressInServiceZone(coordinate);
        return coordinate;
    }

    private void validateAddressInServiceZone(ServiceZone.Coordinate coordinate) {
        ServiceZone activeZone = zoneRepository.findFirstByActiveTrue()
                .orElseThrow(() -> new IllegalArgumentException("Активная зона обслуживания не настроена"));

        if (activeZone.getCoordinates() == null || activeZone.getCoordinates().isEmpty()) {
            throw new IllegalArgumentException("Активная зона не содержит координат");
        }

        boolean inZone = geoUtils.isPointInPolygon(
                coordinate.getLat(),
                coordinate.getLng(),
                activeZone.getCoordinates()
        );

        if (!inZone) {
            throw new IllegalArgumentException(
                    String.format(
                            "Этот адрес вне зоны обслуживания (%.6f, %.6f). Выберите адрес внутри активной зоны.",
                            coordinate.getLat(),
                            coordinate.getLng()
                    )
            );
        }
    }

    private OffsetDateTime alignToSlot(LocalDate date, PickupSlot pickupSlot) {
        ZoneId zoneId = ZoneId.systemDefault();
        ZonedDateTime zonedDateTime = ZonedDateTime.of(date, pickupSlot.getStartTime(), zoneId);
        return zonedDateTime.toOffsetDateTime();
    }

    private String normalizeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "not-provided";
        }
        String trimmed = reason.trim();
        return trimmed.length() > 120 ? trimmed.substring(0, 120) : trimmed;
    }
}
