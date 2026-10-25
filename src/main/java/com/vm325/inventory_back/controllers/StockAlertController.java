package com.vm325.inventory_back.controllers;

import com.vm325.inventory_back.dtos.StockAlertResponseDto;
import com.vm325.inventory_back.dtos.SupplierOrderRequestDto;
import com.vm325.inventory_back.services.MailService;
import com.vm325.inventory_back.services.StockAlertService;
import com.vm325.inventory_back.services.SupplierOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stock-alerts")
@RequiredArgsConstructor
public class StockAlertController {
    private final StockAlertService stockAlertService;
    private final SupplierOrderService supplierOrderService;

    @GetMapping("/pending")
    public ResponseEntity<List<StockAlertResponseDto>> findPending() {
        return ResponseEntity.ok(stockAlertService.findPending());
    }

    // Solo Administrador puede marcar una alerta como atendida
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{id}/attend")
    public ResponseEntity<StockAlertResponseDto> attend(@PathVariable Long id) {
        return ResponseEntity.ok(stockAlertService.attend(id));
    }

    // Vista previa del correo de pedido al proveedor (no envía nada)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/supplier-order/preview")
    public ResponseEntity<MailService.RenderedMail> previewSupplierOrder(
            @PathVariable Long id, @Valid @RequestBody SupplierOrderRequestDto dto) {
        return ResponseEntity.ok(supplierOrderService.preview(id, dto.getQuantity()));
    }

    // Envía el pedido al proveedor del producto de la alerta
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{id}/supplier-order")
    public ResponseEntity<StockAlertResponseDto> sendSupplierOrder(
            @PathVariable Long id, @Valid @RequestBody SupplierOrderRequestDto dto) {
        return ResponseEntity.ok(supplierOrderService.send(id, dto.getQuantity()));
    }
}
