package com.example.core.service;

import com.example.core.dto.OrderClusterResponse;
import com.example.core.dto.OrderClusteringResponse;
import com.example.core.model.Order;
import com.example.core.model.Subscription;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderClusteringServiceTest {

    private final OrderClusteringService service = new OrderClusteringService();

    @Test
    void clusterOrdersShouldGroupOrdersInside50MetersRadius() {
        OffsetDateTime base = OffsetDateTime.now()
                .plusDays(1)
                .withHour(8)
                .withMinute(30)
                .withSecond(0)
                .withNano(0);

        Order order1 = Order.builder()
                .id(1L)
                .address("Оренбург, ул. Первая, 1")
                .lat(51.820000)
                .lng(55.170000)
                .pickupTime(base)
                .build();

        Order order2 = Order.builder()
                .id(2L)
                .address("Оренбург, ул. Первая, 2")
                .lat(51.820180)
                .lng(55.170030)
                .pickupTime(base.plusMinutes(15))
                .build();

        Order order3 = Order.builder()
                .id(3L)
                .address("Оренбург, ул. Другая, 1")
                .lat(51.821100)
                .lng(55.171100)
                .pickupTime(base.plusMinutes(25))
                .build();

        OrderClusteringResponse response = service.clusterOrders(List.of(order1, order2, order3), 50.0);

        assertEquals(3, response.getSourceOrders());
        assertEquals(3, response.getClusteredOrders());
        assertEquals(0, response.getSkippedWithoutCoordinates());
        assertEquals(2, response.getClusters().size());

        OrderClusterResponse firstCluster = response.getClusters().get(0);
        OrderClusterResponse secondCluster = response.getClusters().get(1);

        assertEquals(2, firstCluster.getOrderCount());
        assertEquals(1, secondCluster.getOrderCount());
        assertEquals("08:00-11:00", firstCluster.getPickupSlot());
        assertEquals(base.toLocalDate().toString(), firstCluster.getPickupDate());
    }

    @Test
    void clusterOrdersShouldUseSubscriptionCoordinatesFallback() {
        Subscription subscription = Subscription.builder()
                .id(11L)
                .serviceLat(51.825000)
                .serviceLng(55.165000)
                .build();

        Order order = Order.builder()
                .id(10L)
                .address("Оренбург, ул. Тестовая, 10")
                .pickupTime(OffsetDateTime.now().plusDays(1).withHour(8).withMinute(20).withSecond(0).withNano(0))
                .subscription(subscription)
                .build();

        OrderClusteringResponse response = service.clusterOrders(List.of(order), 50.0);

        assertEquals(1, response.getSourceOrders());
        assertEquals(1, response.getClusteredOrders());
        assertEquals(0, response.getSkippedWithoutCoordinates());
        assertEquals(1, response.getClusters().size());
        assertEquals(1, response.getClusters().get(0).getOrderCount());
    }
}
