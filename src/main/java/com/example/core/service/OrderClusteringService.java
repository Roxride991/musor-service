package com.example.core.service;

import com.example.core.dto.OrderClusterResponse;
import com.example.core.dto.OrderClusteringResponse;
import com.example.core.model.Order;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OrderClusteringService {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;
    private static final LocalTime SLOT_1_START = LocalTime.of(8, 0);
    private static final LocalTime SLOT_1_END = LocalTime.of(11, 0);
    private static final LocalTime SLOT_2_START = LocalTime.of(13, 0);
    private static final LocalTime SLOT_2_END = LocalTime.of(16, 0);
    private static final LocalTime SLOT_3_START = LocalTime.of(19, 0);
    private static final LocalTime SLOT_3_END = LocalTime.of(21, 0);
    private static final String SLOT_8_11 = "08:00-11:00";
    private static final String SLOT_13_16 = "13:00-16:00";
    private static final String SLOT_19_21 = "19:00-21:00";
    private static final String SLOT_CUSTOM = "CUSTOM";
    private static final String DATE_UNKNOWN = "UNKNOWN";

    public OrderClusteringResponse clusterOrders(List<Order> orders, double radiusMeters) {
        double safeRadiusMeters = normalizeRadius(radiusMeters);

        List<OrderPoint> points = new ArrayList<>();
        int skippedWithoutCoordinates = 0;
        if (orders != null) {
            for (Order order : orders) {
                OrderPoint point = toPoint(order);
                if (point == null) {
                    skippedWithoutCoordinates++;
                    continue;
                }
                points.add(point);
            }
        }

        List<OrderClusterResponse> clusters = buildClusters(points, safeRadiusMeters);
        int clusteredOrders = clusters.stream().mapToInt(OrderClusterResponse::getOrderCount).sum();

        return OrderClusteringResponse.builder()
                .radiusMeters(safeRadiusMeters)
                .sourceOrders(orders == null ? 0 : orders.size())
                .clusteredOrders(clusteredOrders)
                .skippedWithoutCoordinates(skippedWithoutCoordinates)
                .clusters(clusters)
                .build();
    }

    private List<OrderClusterResponse> buildClusters(List<OrderPoint> points, double radiusMeters) {
        if (points.isEmpty()) {
            return List.of();
        }

        List<OrderClusterResponse> clusters = new ArrayList<>();
        Map<PickupKey, List<Integer>> buckets = bucketByDateAndSlot(points);

        for (Map.Entry<PickupKey, List<Integer>> entry : buckets.entrySet()) {
            PickupKey key = entry.getKey();
            List<Integer> indexes = entry.getValue();
            if (indexes.isEmpty()) {
                continue;
            }

            boolean[][] adjacency = buildAdjacency(points, indexes, radiusMeters);
            Set<Integer> visited = new HashSet<>();
            int clusterCounter = 1;
            for (int idx = 0; idx < indexes.size(); idx++) {
                if (visited.contains(idx)) {
                    continue;
                }
                List<Integer> component = bfsComponent(idx, adjacency, visited);
                String clusterId = key.dateLabel + "_" + key.slotLabel.replace(":", "").replace("-", "_") + "_" + clusterCounter++;
                clusters.add(toCluster(clusterId, key, component, indexes, points));
            }
        }

        return clusters.stream()
                .sorted(Comparator
                        .comparing(OrderClusterResponse::getPickupFrom, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OrderClusterResponse::getOrderCount, Comparator.reverseOrder()))
                .toList();
    }

    private Map<PickupKey, List<Integer>> bucketByDateAndSlot(List<OrderPoint> points) {
        Map<PickupKey, List<Integer>> buckets = new HashMap<>();
        for (int idx = 0; idx < points.size(); idx++) {
            PickupKey key = resolvePickupKey(points.get(idx).pickupTime);
            buckets.computeIfAbsent(key, ignored -> new ArrayList<>()).add(idx);
        }
        return buckets;
    }

    private boolean[][] buildAdjacency(List<OrderPoint> points, List<Integer> indexes, double radiusMeters) {
        int n = indexes.size();
        boolean[][] adjacency = new boolean[n][n];
        for (int i = 0; i < n; i++) {
            adjacency[i][i] = true;
            for (int j = i + 1; j < n; j++) {
                OrderPoint first = points.get(indexes.get(i));
                OrderPoint second = points.get(indexes.get(j));
                double distance = haversineMeters(
                        first.lat,
                        first.lng,
                        second.lat,
                        second.lng
                );
                if (distance <= radiusMeters) {
                    adjacency[i][j] = true;
                    adjacency[j][i] = true;
                }
            }
        }
        return adjacency;
    }

    private List<Integer> bfsComponent(int start, boolean[][] adjacency, Set<Integer> visited) {
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        List<Integer> component = new ArrayList<>();
        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            int current = queue.poll();
            component.add(current);
            for (int next = 0; next < adjacency.length; next++) {
                if (!adjacency[current][next] || visited.contains(next)) {
                    continue;
                }
                visited.add(next);
                queue.add(next);
            }
        }
        return component;
    }

    private OrderClusterResponse toCluster(
            String clusterId,
            PickupKey key,
            List<Integer> component,
            List<Integer> indexes,
            List<OrderPoint> points
    ) {
        double latSum = 0.0;
        double lngSum = 0.0;
        OffsetDateTime pickupFrom = null;
        OffsetDateTime pickupTo = null;
        List<Long> orderIds = new ArrayList<>();
        List<String> addresses = new ArrayList<>();

        for (Integer index : component) {
            OrderPoint point = points.get(indexes.get(index));
            latSum += point.lat;
            lngSum += point.lng;
            orderIds.add(point.orderId);
            addresses.add(point.address);

            if (point.pickupTime != null) {
                if (pickupFrom == null || point.pickupTime.isBefore(pickupFrom)) {
                    pickupFrom = point.pickupTime;
                }
                if (pickupTo == null || point.pickupTime.isAfter(pickupTo)) {
                    pickupTo = point.pickupTime;
                }
            }
        }

        int count = component.size();
        return OrderClusterResponse.builder()
                .clusterId(clusterId)
                .orderCount(count)
                .pickupDate(key.dateLabel)
                .pickupSlot(key.slotLabel)
                .centroidLat(latSum / count)
                .centroidLng(lngSum / count)
                .pickupFrom(pickupFrom)
                .pickupTo(pickupTo)
                .orderIds(orderIds)
                .addresses(addresses)
                .build();
    }

    private OrderPoint toPoint(Order order) {
        if (order == null || order.getId() == null) {
            return null;
        }

        Double lat = order.getLat();
        Double lng = order.getLng();
        if ((lat == null || lng == null) && order.getSubscription() != null) {
            lat = lat == null ? order.getSubscription().getServiceLat() : lat;
            lng = lng == null ? order.getSubscription().getServiceLng() : lng;
        }

        if (lat == null || lng == null) {
            return null;
        }

        return new OrderPoint(
                order.getId(),
                lat,
                lng,
                order.getPickupTime(),
                order.getAddress() == null ? "" : order.getAddress().trim()
        );
    }

    private double normalizeRadius(double radiusMeters) {
        if (Double.isNaN(radiusMeters) || Double.isInfinite(radiusMeters)) {
            return 50.0;
        }
        return Math.max(10.0, Math.min(500.0, radiusMeters));
    }

    private PickupKey resolvePickupKey(OffsetDateTime pickupTime) {
        if (pickupTime == null) {
            return new PickupKey(DATE_UNKNOWN, SLOT_CUSTOM);
        }
        LocalDate date = pickupTime.toLocalDate();
        LocalTime time = pickupTime.toLocalTime();
        return new PickupKey(date.toString(), resolveSlotLabel(time));
    }

    private String resolveSlotLabel(LocalTime time) {
        if (time == null) {
            return SLOT_CUSTOM;
        }
        if (isBetween(time, SLOT_1_START, SLOT_1_END)) {
            return SLOT_8_11;
        }
        if (isBetween(time, SLOT_2_START, SLOT_2_END)) {
            return SLOT_13_16;
        }
        if (isBetween(time, SLOT_3_START, SLOT_3_END)) {
            return SLOT_19_21;
        }
        return SLOT_CUSTOM;
    }

    private boolean isBetween(LocalTime time, LocalTime start, LocalTime endExclusive) {
        return !time.isBefore(start) && time.isBefore(endExclusive);
    }

    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    private record OrderPoint(
            Long orderId,
            double lat,
            double lng,
            OffsetDateTime pickupTime,
            String address
    ) {
    }

    private record PickupKey(
            String dateLabel,
            String slotLabel
    ) {
    }
}
