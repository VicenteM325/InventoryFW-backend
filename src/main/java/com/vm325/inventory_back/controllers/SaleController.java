package com.vm325.inventory_back.controllers;

import com.vm325.inventory_back.dtos.SaleRequestDto;
import com.vm325.inventory_back.dtos.SaleResponseDto;
import com.vm325.inventory_back.services.SaleReceiptService;
import com.vm325.inventory_back.services.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sales")
@RequiredArgsConstructor
public class SaleController {
    private final SaleService saleService;
    private final SaleReceiptService saleReceiptService;

    @PostMapping
    public ResponseEntity<SaleResponseDto> registerSale(@Valid @RequestBody SaleRequestDto dto,
                                                        Authentication authentication) {
        String username = authentication.getName();
        SaleResponseDto sale = saleService.registerSale(dto, username);
        // Después de confirmar la venta: un fallo del correo no debe afectarla ni retener bloqueos.
        sale.setReceiptStatus(saleReceiptService.sendReceipt(sale, dto.getCustomerEmail(), username));
        return ResponseEntity.status(HttpStatus.CREATED).body(sale);
    }

    @GetMapping("/recent")
    public ResponseEntity<List<SaleResponseDto>> findRecent() {
        return ResponseEntity.ok(saleService.findRecent());
    }
}
