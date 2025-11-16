package com.example.core.service;

import com.example.core.model.ServiceZone;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Утилиты для георасчётов (проверка попадания точки в полигон и т.п.).
 */
@Component
public class GeoUtils {

    /**
     * Проверяет, находится ли точка внутри многоугольника (алгоритм Ray Casting),
     * где x=lng, y=lat. Порядок вершин произвольный (замыкать не требуется).
     */
    public boolean isPointInPolygon(double lat, double lng, List<ServiceZone.Coordinate> polygon) {
        int n = polygon.size();
        if (n < 3) return false;

        boolean inside = false;
        // Используем ray casting, где x=lng, y=lat
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon.get(i).getLng();
            double yi = polygon.get(i).getLat();
            double xj = polygon.get(j).getLng();
            double yj = polygon.get(j).getLat();

            // Проверяем, пересекает ли горизонтальный луч (идущий вправо от точки) ребро полигона
            // Условие 1: точка должна быть между yi и yj по вертикали
            boolean yBetween = (yi > lat) != (yj > lat);
            
            // Условие 2: горизонтальное ребро пропускаем (yj == yi)
            if (Math.abs(yj - yi) < 1e-10) {
                continue; // Горизонтальное ребро не пересекается с горизонтальным лучом
            }
            
            // Вычисляем x-координату пересечения луча с ребром
            double xIntersect = (xj - xi) * (lat - yi) / (yj - yi) + xi;
            
            // Проверяем, находится ли точка слева от точки пересечения
            boolean intersect = yBetween && (lng < xIntersect);
            
            if (intersect) inside = !inside;
        }
        return inside;
    }
}