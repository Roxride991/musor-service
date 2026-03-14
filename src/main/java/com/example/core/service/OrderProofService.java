package com.example.core.service;

import com.example.core.dto.ProofPhotoResponse;
import com.example.core.dto.UploadProofPhotoRequest;
import com.example.core.model.Order;
import com.example.core.model.OrderProofPhoto;
import com.example.core.model.OrderStatus;
import com.example.core.model.ProofStage;
import com.example.core.model.User;
import com.example.core.model.UserRole;
import com.example.core.repository.OrderProofPhotoRepository;
import com.example.core.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProofService {

    private final OrderRepository orderRepository;
    private final OrderProofPhotoRepository proofPhotoRepository;
    private final AuditService auditService;

    @Value("${storage.proofs.dir:storage/proofs}")
    private String proofsDirectory;

    @Value("${storage.proofs.max-size-bytes:5242880}")
    private long maxSizeBytes;

    @Transactional
    public ProofPhotoResponse uploadProof(Long orderId, User courier, UploadProofPhotoRequest request) {
        if (courier == null || courier.getUserRole() != UserRole.COURIER) {
            throw new IllegalStateException("Только курьер может загружать фото-подтверждение");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));

        if (order.getCourier() == null
                || order.getCourier().getId() == null
                || !order.getCourier().getId().equals(courier.getId())) {
            throw new IllegalStateException("Фото можно загрузить только для вашего заказа");
        }

        ensureStageAllowed(order, request.getStage());

        DecodedImage decoded = decodeImage(request.getImageBase64(), request.getMimeType());
        if (decoded.bytes.length > maxSizeBytes) {
            throw new IllegalArgumentException("Размер фото превышает допустимый лимит");
        }

        String ext = extensionByMime(decoded.mimeType);
        String fileName = request.getStage().name().toLowerCase() + "-" + System.currentTimeMillis() + "-" + UUID.randomUUID() + ext;
        Path directory = Path.of(proofsDirectory, "order-" + orderId).toAbsolutePath().normalize();
        Path filePath = directory.resolve(fileName).normalize();

        if (!filePath.startsWith(directory)) {
            throw new IllegalStateException("Неверный путь для хранения фото");
        }

        try {
            Files.createDirectories(directory);
            Files.write(filePath, decoded.bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось сохранить фото", e);
        }

        OrderProofPhoto proof = OrderProofPhoto.builder()
                .order(order)
                .courier(courier)
                .stage(request.getStage())
                .storagePath(filePath.toString())
                .mimeType(decoded.mimeType)
                .sizeBytes(decoded.bytes.length)
                .note(trimNote(request.getNote()))
                .build();

        OrderProofPhoto saved = proofPhotoRepository.save(proof);

        auditService.log(
                "ORDER_PROOF_UPLOAD",
                "SUCCESS",
                courier,
                "ORDER_ID",
                String.valueOf(orderId),
                "Proof uploaded: stage=" + request.getStage() + ", proofId=" + saved.getId(),
                null
        );

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ProofPhotoResponse> listProofs(Long orderId, User actor) {
        ensureCanReadOrder(orderId, actor);
        return proofPhotoRepository.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProofContent loadProofContent(Long orderId, Long proofId, User actor) {
        ensureCanReadOrder(orderId, actor);

        OrderProofPhoto proof = proofPhotoRepository.findByIdAndOrderId(proofId, orderId)
                .orElseThrow(() -> new IllegalArgumentException("Фото-подтверждение не найдено"));

        Path path = Path.of(proof.getStoragePath()).toAbsolutePath().normalize();
        Path baseDir = Path.of(proofsDirectory).toAbsolutePath().normalize();
        if (!path.startsWith(baseDir)) {
            throw new IllegalStateException("Некорректный путь хранения фото");
        }
        try {
            byte[] bytes = Files.readAllBytes(path);
            return new ProofContent(bytes, proof.getMimeType());
        } catch (IOException e) {
            throw new IllegalStateException("Не удалось прочитать файл фото", e);
        }
    }

    private void ensureCanReadOrder(Long orderId, User actor) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Заказ не найден"));

        if (actor.getUserRole() == UserRole.ADMIN) {
            return;
        }

        if (actor.getUserRole() == UserRole.COURIER
                && order.getCourier() != null
                && order.getCourier().getId() != null
                && order.getCourier().getId().equals(actor.getId())) {
            return;
        }

        if (actor.getUserRole() == UserRole.CLIENT
                && order.getClient() != null
                && order.getClient().getId() != null
                && order.getClient().getId().equals(actor.getId())) {
            return;
        }

        throw new IllegalStateException("Доступ к фото запрещен");
    }

    private void ensureStageAllowed(Order order, ProofStage stage) {
        if (stage == ProofStage.PICKUP) {
            if (order.getStatus() == OrderStatus.ACCEPTED || order.getStatus() == OrderStatus.PUBLISHED) {
                throw new IllegalStateException("Фото PICKUP можно загрузить после выезда на заказ");
            }
            return;
        }

        if (stage == ProofStage.COMPLETION) {
            if (order.getStatus() != OrderStatus.PICKED_UP && order.getStatus() != OrderStatus.COMPLETED) {
                throw new IllegalStateException("Фото COMPLETION можно загрузить на этапе PICKED_UP или COMPLETED");
            }
        }
    }

    private DecodedImage decodeImage(String raw, String explicitMimeType) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Фото обязательно");
        }

        String working = raw.trim();
        String mimeType = explicitMimeType == null || explicitMimeType.isBlank()
                ? "image/jpeg"
                : explicitMimeType.trim().toLowerCase();

        if (working.startsWith("data:")) {
            int comma = working.indexOf(',');
            if (comma < 0) {
                throw new IllegalArgumentException("Некорректный формат data URI");
            }
            String header = working.substring(5, comma).toLowerCase();
            int semicolon = header.indexOf(';');
            if (semicolon > 0) {
                mimeType = header.substring(0, semicolon);
            } else {
                mimeType = header;
            }
            working = working.substring(comma + 1);
        }

        long estimatedDecodedSize = estimateDecodedSize(working);
        if (estimatedDecodedSize > maxSizeBytes) {
            throw new IllegalArgumentException("Размер фото превышает допустимый лимит");
        }

        if (!mimeType.equals("image/jpeg") && !mimeType.equals("image/png") && !mimeType.equals("image/webp")) {
            throw new IllegalArgumentException("Поддерживаются только JPEG/PNG/WEBP");
        }

        try {
            byte[] bytes = Base64.getDecoder().decode(working);
            return new DecodedImage(bytes, mimeType);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Некорректный base64 контент изображения");
        }
    }

    private long estimateDecodedSize(String base64) {
        if (base64 == null || base64.isBlank()) {
            return 0L;
        }
        int length = base64.length();
        int padding = 0;
        if (length >= 1 && base64.charAt(length - 1) == '=') {
            padding++;
        }
        if (length >= 2 && base64.charAt(length - 2) == '=') {
            padding++;
        }
        return ((long) length * 3L) / 4L - padding;
    }

    private String extensionByMime(String mimeType) {
        return switch (mimeType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private String trimNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        String trimmed = note.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }

    private ProofPhotoResponse toResponse(OrderProofPhoto photo) {
        return ProofPhotoResponse.builder()
                .id(photo.getId())
                .orderId(photo.getOrder().getId())
                .courierId(photo.getCourier().getId())
                .stage(photo.getStage())
                .mimeType(photo.getMimeType())
                .sizeBytes(photo.getSizeBytes())
                .note(photo.getNote())
                .contentUrl("/api/orders/" + photo.getOrder().getId() + "/proofs/" + photo.getId() + "/content")
                .createdAt(photo.getCreatedAt())
                .build();
    }

    private record DecodedImage(byte[] bytes, String mimeType) {
    }

    @lombok.Value
    public static class ProofContent {
        byte[] bytes;
        String mimeType;
    }
}
