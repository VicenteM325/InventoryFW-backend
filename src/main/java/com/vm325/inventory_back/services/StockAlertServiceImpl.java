package com.vm325.inventory_back.services;

import com.vm325.inventory_back.dtos.StockAlertResponseDto;
import com.vm325.inventory_back.entities.StockAlert;
import com.vm325.inventory_back.enums.StockAlertStatus;
import com.vm325.inventory_back.repositories.StockAlertRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class StockAlertServiceImpl implements StockAlertService {

    private final StockAlertRepository stockAlertRepository;

    @Override
    public List<StockAlertResponseDto> findPending() {
        return stockAlertRepository.findByStatusOrderByCreatedAtDesc(StockAlertStatus.PENDIENTE)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    public StockAlertResponseDto attend(Long id) {
        StockAlert alert = stockAlertRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Alerta no encontrada con id " + id));

        // Con proveedor asociado, la alerta se atiende cuando ya se le envió el pedido.
        // Sin proveedor no hay a quién pedirle, así que se puede atender directamente.
        if (alert.getProduct().getSupplier() != null && alert.getOrderedAt() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Primero envía el pedido al proveedor para poder marcar la alerta como atendida");
        }

        alert.setStatus(StockAlertStatus.ATENDIDA);
        alert.setAttendedAt(LocalDateTime.now());

        return toDto(stockAlertRepository.save(alert));
    }

    @Override
    public StockAlertResponseDto toResponse(StockAlert alert) {
        return toDto(alert);
    }

    private StockAlertResponseDto toDto(StockAlert alert) {
        var supplier = alert.getProduct().getSupplier();
        return StockAlertResponseDto.builder()
                .stockAlertId(alert.getStockAlertId())
                .productId(alert.getProduct().getProductId())
                .productName(alert.getProduct().getName())
                .supplierId(supplier != null ? supplier.getSupplierId() : null)
                .supplierName(supplier != null ? supplier.getName() : null)
                .supplierEmail(supplier != null ? supplier.getEmail() : null)
                .minStock(alert.getProduct().getMinStock())
                .orderedQuantity(alert.getOrderedQuantity())
                .orderedAt(alert.getOrderedAt())
                .stockAtAlert(alert.getStockAtAlert())
                .status(alert.getStatus())
                .createdAt(alert.getCreatedAt())
                .build();
    }
}